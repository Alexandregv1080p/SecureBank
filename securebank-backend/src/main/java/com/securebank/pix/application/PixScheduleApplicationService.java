package com.securebank.pix.application;

import com.securebank.account.application.AccountApplicationService;
import com.securebank.account.domain.Account;
import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.outbox.application.OutboxService;
import com.securebank.pix.domain.PixSchedule;
import com.securebank.pix.domain.PixTransfer;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.application.TransactionalRetry;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.DomainException;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PixScheduleId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Pix agendado: criar, listar, cancelar e (pelo agendador) executar os que venceram. */
@Service
@Transactional
public class PixScheduleApplicationService {

    static final int MAX_PENDING_PER_CUSTOMER = 20;
    static final int BATCH = 50;

    private static final Logger log = LoggerFactory.getLogger(PixScheduleApplicationService.class);

    private final PixScheduleRepository schedules;
    private final PixApplicationService pix;
    private final AccountApplicationService accountService;
    private final CustomerApplicationService customerService;
    private final BankTime time;
    private final AuditService audit;
    private final OutboxService outbox;
    private final TransactionalRetry retry;

    public PixScheduleApplicationService(PixScheduleRepository schedules, PixApplicationService pix,
            AccountApplicationService accountService, CustomerApplicationService customerService, BankTime time,
            AuditService audit, OutboxService outbox, TransactionalRetry retry) {
        this.schedules = schedules;
        this.pix = pix;
        this.accountService = accountService;
        this.customerService = customerService;
        this.time = time;
        this.audit = audit;
        this.outbox = outbox;
        this.retry = retry;
    }

    public PixSchedule schedule(CustomerId requester, AccountId sourceAccountId, String rawKey, BigDecimal amount,
            String message, LocalDate scheduledFor) {
        customerService.requireActive(requester);
        Account source = accountService.findOwned(requester, sourceAccountId);
        // a chave tem de existir AGORA (erro de digitação aparece já, não no dia); o valor canônico é o que se guarda
        PixApplicationService.Lookup destination = pix.lookup(requester, rawKey);
        if (schedules.countPendingByCustomer(requester) >= MAX_PENDING_PER_CUSTOMER) {
            throw ApplicationException.unprocessable("PIX_SCHEDULE_LIMIT_REACHED",
                    "You can have at most " + MAX_PENDING_PER_CUSTOMER + " scheduled Pix");
        }
        PixSchedule schedule = PixSchedule.create(requester, source.id(), destination.key(), destination.maskedName(),
                new Money(amount, source.balance().currency()), message, scheduledFor, time.today(), time.now());
        schedules.save(schedule);
        audit.record(AuditEntry.of(AuditEvent.PIX_SCHEDULED).account(source.id()).detail(schedule.id().toString()));
        return schedule;
    }

    @Transactional(readOnly = true)
    public PageResult<PixSchedule> list(CustomerId requester, int page, int size) {
        PageResult.validate(page, size);
        return schedules.findByCustomer(requester, page, size);
    }

    public void cancel(CustomerId requester, PixScheduleId id) {
        PixSchedule schedule = schedules.findById(id).filter(s -> s.isOwnedBy(requester))
                .orElseThrow(() -> ApplicationException.notFound("Scheduled Pix"));
        schedule.cancel(time.now());
        schedules.save(schedule);
        audit.record(AuditEntry.of(AuditEvent.PIX_SCHEDULE_CANCELED).account(schedule.sourceAccountId())
                .detail(schedule.id().toString()));
    }

    // ------------------------------------------------------------------ execução (agendador)
    /**
     * Executa os agendamentos que venceram. Cada um roda na sua própria transação: o dinheiro e a marcação EXECUTED
     * saem juntos ou nenhum dos dois. Se uma regra do dia recusar (saldo, limite, chave, conta), a transação desfaz e o
     * agendamento é marcado FAILED à parte, com aviso ao cliente. Nunca há segunda tentativa automática.
     *
     * @return quantos foram tratados (executados ou falhos) neste lote
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public int executeDue() {
        LocalDate today = time.today();
        int handled = 0;
        for (PixScheduleId id : schedules.findDueIds(today, BATCH)) {
            try {
                Boolean ran = retry.execute(() -> runOne(id, today));
                if (Boolean.TRUE.equals(ran)) {
                    handled++;
                }
            } catch (DomainException e) {
                handled += failOne(id, today, e.code());
            } catch (ApplicationException e) {
                handled += failOne(id, today, e.code());
            } catch (RuntimeException e) {
                log.error("Scheduled Pix {} could not be processed; will retry on the next tick", id, e);
            }
        }
        return handled;
    }

    private boolean runOne(PixScheduleId id, LocalDate today) {
        PixSchedule schedule = schedules.lockDue(id, today).orElse(null);
        if (schedule == null) {
            return false; // outra instância já tratou (ou está tratando): pula
        }
        PixTransfer sent = pix.doSend(schedule.customerId(), schedule.sourceAccountId(), schedule.key(),
                schedule.amount().amount(), schedule.message());
        schedule.markExecuted(sent.id(), time.now());
        schedules.save(schedule);
        return true;
    }

    private int failOne(PixScheduleId id, LocalDate today, String reason) {
        Boolean failed = retry.execute(() -> {
            PixSchedule schedule = schedules.lockDue(id, today).orElse(null);
            if (schedule == null) {
                return false;
            }
            Instant now = time.now();
            schedule.markFailed(reason, now);
            schedules.save(schedule);
            audit.record(AuditEntry.of(AuditEvent.PIX_SCHEDULE_FAILED).account(schedule.sourceAccountId()).detail(reason));
            outbox.record("PixScheduleFailed", "Pix", schedule.id().toString(), Map.of(
                    "scheduleId", schedule.id().toString(), "accountId", schedule.sourceAccountId().toString(),
                    "amount", schedule.amount().amount().toPlainString(),
                    "currency", schedule.amount().currency().getCurrencyCode(), "reason", schedule.failureReason()));
            return true;
        });
        return Boolean.TRUE.equals(failed) ? 1 : 0;
    }

    /** Usado só pelos testes e pelo agendador para listar o que venceu. */
    List<PixScheduleId> dueIds() {
        return schedules.findDueIds(time.today(), BATCH);
    }
}
