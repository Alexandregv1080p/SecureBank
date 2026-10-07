package com.securebank.notification.application;

import com.securebank.account.application.AccountRepository;
import com.securebank.authentication.application.UserRepository;
import com.securebank.notification.domain.Notification;
import com.securebank.outbox.domain.EventEnvelope;
import com.securebank.piggy.application.PiggyRepository;
import com.securebank.shared.domain.PiggyId;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.UserId;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consumidor dos eventos de domínio: transforma evento em aviso ao cliente. Idempotente por evento. */
@Service
public class NotificationService {

    static final String CONSUMER = "notifications";
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final ProcessedEvents processed;
    private final AccountRepository accounts;
    private final UserRepository users;
    private final PiggyRepository piggies;
    private final BankTime time;

    public NotificationService(NotificationRepository notifications, ProcessedEvents processed,
            AccountRepository accounts, UserRepository users, PiggyRepository piggies, BankTime time) {
        this.notifications = notifications;
        this.processed = processed;
        this.accounts = accounts;
        this.users = users;
        this.piggies = piggies;
        this.time = time;
    }

    /**
     * O registro "processado" e os avisos são gravados na MESMA transação: ou os dois existem ou nenhum. Assim uma
     * reentrega do Kafka (at-least-once) não duplica avisos, e uma falha no meio não marca o evento como tratado.
     * Payload malformado lança exceção (retry e depois DLT, configurados no listener).
     */
    @Transactional
    public void handle(EventEnvelope event) {
        if (!processed.markProcessed(CONSUMER, event.eventId(), time.now())) {
            log.debug("Event {} already processed, skipping", event.eventId());
            return;
        }
        switch (event.eventType()) {
            case "TransferCompleted" -> {
                String amount = event.text("amount") + " " + event.text("currency");
                notifyOwner(event, event.text("sourceAccountId"), "TRANSFER_SENT", "Transferência enviada",
                        "Você enviou " + amount + " por transferência.");
                notifyOwner(event, event.text("destinationAccountId"), "TRANSFER_RECEIVED", "Transferência recebida",
                        "Você recebeu " + amount + " por transferência.");
            }
            case "PixCompleted" -> {
                String amount = event.text("amount") + " " + event.text("currency");
                notifyOwner(event, event.text("sourceAccountId"), "PIX_SENT", "Pix enviado", "Você enviou " + amount + " por Pix.");
                notifyOwner(event, event.text("destinationAccountId"), "PIX_RECEIVED", "Pix recebido", "Você recebeu " + amount + " por Pix.");
            }
            case "PixRefunded" -> {
                String amount = event.text("amount") + " " + event.text("currency");
                notifyOwner(event, event.text("sourceAccountId"), "PIX_REFUND_SENT", "Devolução enviada", "Você devolveu " + amount + " de um Pix.");
                notifyOwner(event, event.text("destinationAccountId"), "PIX_REFUND_RECEIVED", "Devolução recebida", "Você recebeu " + amount + " de volta de um Pix.");
            }
            case "PixScheduleFailed" -> notifyOwner(event, event.text("accountId"), "PIX_SCHEDULE_FAILED", "Pix agendado não realizado",
                    "Seu Pix agendado de " + event.text("amount") + " " + event.text("currency") + " não foi realizado: "
                            + scheduleReason(event.text("reason")) + ".");
            case "InvestmentApplied" -> notifyOwner(event, event.text("accountId"), "INVEST_APPLIED", "Aplicação realizada",
                    "Você aplicou " + event.text("amount") + " " + event.text("currency") + " em " + event.text("product") + ".");
            case "InvestmentMatured" -> notifyOwner(event, event.text("accountId"), "INVEST_MATURED", "Investimento venceu",
                    "Seu " + event.text("product") + " venceu e já pode ser resgatado.");
            case "InvestmentRedeemed" -> notifyOwner(event, event.text("accountId"), "INVEST_REDEEMED", "Resgate realizado",
                    "Você resgatou " + event.text("amount") + " " + event.text("currency") + " líquidos de " + event.text("product") + ".");
            case "PaymentCompleted" -> notifyOwner(event, event.text("accountId"), "PAYMENT_DONE", "Pagamento realizado",
                    "Seu pagamento de " + event.text("amount") + " " + event.text("currency") + " foi concluído.");
            case "AccountBlocked" -> notifyOwner(event, event.text("accountId"), "ACCOUNT_BLOCKED", "Conta bloqueada",
                    "Sua conta foi bloqueada. Fale com o suporte.");
            case "AccountUnblocked" -> notifyOwner(event, event.text("accountId"), "ACCOUNT_UNBLOCKED",
                    "Conta desbloqueada", "Sua conta foi desbloqueada.");
            case "PiggyGoalReached" -> piggies.findById(PiggyId.of(event.text("piggyId")))
                    .ifPresent(p -> save(p.customerId(), event, "PIGGY_GOAL", "Meta alcançada!",
                            "Seu porquinho \"" + p.name() + "\" chegou à meta de " + p.goal().amount().toPlainString()
                                    + " " + p.goal().currency().getCurrencyCode() + "."));
            case "UserLoggedIn" -> users.findById(UserId.of(event.text("userId")))
                    .map(u -> u.customerId())
                    .ifPresent(customerId -> save(customerId, event, "NEW_LOGIN", "Novo acesso à sua conta",
                            "Houve um login na sua conta. Se não foi você, troque a senha."));
            default -> log.debug("No notification for event type {}", event.eventType());
        }
    }

    @Transactional(readOnly = true)
    public PageResult<Notification> list(CustomerId customerId, int page, int size) {
        PageResult.validate(page, size);
        return notifications.findByCustomer(customerId, page, size);
    }

    @Transactional
    public void markRead(CustomerId customerId, UUID id) {
        if (!notifications.markRead(id, customerId, time.now())) {
            throw ApplicationException.notFound("Notification");
        }
    }

    /** Motivo do agendamento que falhou, em linguagem de gente. */
    private static String scheduleReason(String code) {
        return switch (code) {
            case "INSUFFICIENT_FUNDS" -> "saldo insuficiente";
            case "LIMIT_EXCEEDED" -> "o limite do Pix foi excedido";
            case "NOT_FOUND" -> "a chave do destinatário não existe mais";
            case "ACCOUNT_NOT_ACTIVE" -> "uma das contas não está ativa";
            case "CUSTOMER_NOT_ACTIVE" -> "seu cadastro não está ativo";
            default -> "motivo " + code;
        };
    }

    private void notifyOwner(EventEnvelope event, String accountId, String type, String title, String body) {
        Optional<CustomerId> owner = accounts.findById(AccountId.of(accountId)).map(a -> a.customerId());
        owner.ifPresent(customerId -> save(customerId, event, type, title, body));
    }

    private void save(CustomerId customerId, EventEnvelope event, String type, String title, String body) {
        notifications.save(Notification.create(customerId, type, title, body, event.eventId(), time.now()));
    }
}
