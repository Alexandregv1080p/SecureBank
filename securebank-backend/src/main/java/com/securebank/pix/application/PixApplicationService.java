package com.securebank.pix.application;

import com.securebank.account.application.AccountApplicationService;
import com.securebank.account.application.AccountRepository;
import com.securebank.account.domain.Account;
import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.customer.application.CustomerRepository;
import com.securebank.customer.domain.Customer;
import com.securebank.limit.application.LimitUsage;
import com.securebank.limit.domain.LimitType;
import com.securebank.outbox.application.OutboxService;
import com.securebank.pix.domain.EndToEndId;
import com.securebank.pix.domain.PixKey;
import com.securebank.pix.domain.PixKeyType;
import com.securebank.pix.domain.PixMasks;
import com.securebank.pix.domain.PixService;
import com.securebank.pix.domain.PixTransfer;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.application.TransactionalRetry;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.DomainException;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PixKeyId;
import com.securebank.shared.domain.PixTransferId;
import com.securebank.transaction.application.TransactionRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso do Pix (simulado dentro do próprio banco). Pontos de segurança: o VALOR da chave nunca vem do cliente
 * (CPF, e-mail e celular são lidos do cadastro dele, então ninguém registra a chave de outra pessoa); a consulta de chave
 * devolve só o nome mascarado (e é limitada por taxa, contra enumeração); chave inexistente e de conta bloqueada não
 * são distinguíveis para quem consulta.
 */
@Service
@Transactional
public class PixApplicationService {

    static final int MAX_KEYS_PER_CUSTOMER = 5;

    /** Resultado da consulta de uma chave: o que a tela de confirmação mostra antes de enviar. */
    public record Lookup(PixKeyType type, String key, String maskedName, String maskedDocument, boolean ownAccount) {}

    /** Item do histórico, já do ponto de vista de quem consulta. */
    public record Entry(PixTransfer pix, boolean sent, String counterpartName, Money refunded, Money refundable) {}

    private final PixService domain = new PixService();
    private final Random random = new SecureRandom();

    private final PixKeyRepository keys;
    private final PixTransferRepository transfers;
    private final AccountApplicationService accountService;
    private final AccountRepository accounts;
    private final CustomerApplicationService customerService;
    private final CustomerRepository customers;
    private final LimitUsage limitUsage;
    private final TransactionRepository transactions;
    private final BankTime time;
    private final AuditService audit;
    private final OutboxService outbox;
    private final TransactionalRetry retry;

    public PixApplicationService(PixKeyRepository keys, PixTransferRepository transfers,
            AccountApplicationService accountService, AccountRepository accounts,
            CustomerApplicationService customerService, CustomerRepository customers, LimitUsage limitUsage,
            TransactionRepository transactions, BankTime time, AuditService audit, OutboxService outbox,
            TransactionalRetry retry) {
        this.keys = keys;
        this.transfers = transfers;
        this.accountService = accountService;
        this.accounts = accounts;
        this.customerService = customerService;
        this.customers = customers;
        this.limitUsage = limitUsage;
        this.transactions = transactions;
        this.time = time;
        this.audit = audit;
        this.outbox = outbox;
        this.retry = retry;
    }

    // ------------------------------------------------------------------ chaves
    public PixKey registerKey(CustomerId requester, AccountId accountId, PixKeyType type) {
        customerService.requireActive(requester);
        Customer customer = customerService.get(requester);
        Account account = accountService.findOwned(requester, accountId);
        if (keys.countByCustomer(requester) >= MAX_KEYS_PER_CUSTOMER) {
            throw ApplicationException.unprocessable("PIX_KEY_LIMIT_REACHED",
                    "You can have at most " + MAX_KEYS_PER_CUSTOMER + " Pix keys");
        }
        String value = switch (type) {
            case CPF -> customer.document().value();
            case EMAIL -> customer.email().value();
            case PHONE -> customer.phone().value();
            case RANDOM -> java.util.UUID.randomUUID().toString();
        };
        PixKey key = PixKey.register(requester, account.id(), type, value, time.now());
        if (keys.findByValue(key.value()).isPresent()) {
            throw ApplicationException.conflict("PIX_KEY_IN_USE", "This Pix key is already registered");
        }
        keys.save(key);
        audit.record(AuditEntry.of(AuditEvent.PIX_KEY_CREATED).account(account.id()).detail(type.name()));
        return key;
    }

    @Transactional(readOnly = true)
    public List<PixKey> listKeys(CustomerId requester) {
        return keys.findByCustomer(requester);
    }

    public void deleteKey(CustomerId requester, PixKeyId id) {
        PixKey key = keys.findById(id).filter(k -> k.isOwnedBy(requester))
                .orElseThrow(() -> ApplicationException.notFound("Pix key"));
        keys.delete(key.id());
        audit.record(AuditEntry.of(AuditEvent.PIX_KEY_DELETED).account(key.accountId()).detail(key.type().name()));
    }

    // ------------------------------------------------------------------ consulta
    @Transactional(readOnly = true)
    public Lookup lookup(CustomerId requester, String rawKey) {
        PixKey key = resolve(rawKey);
        Account destination = destinationOf(key);
        Customer owner = customers.findById(destination.customerId())
                .orElseThrow(() -> ApplicationException.notFound("Pix key"));
        return new Lookup(key.type(), key.value(), PixMasks.name(owner.name()), owner.document().masked(),
                owner.id().equals(requester));
    }

    // ------------------------------------------------------------------ envio
    /** Roda sem transação própria: abre uma por tentativa e repete em conflito de versão (como a transferência). */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PixTransfer send(CustomerId requester, AccountId sourceAccountId, String rawKey, BigDecimal amount,
            String message) {
        try {
            return retry.execute(() -> doSend(requester, sourceAccountId, rawKey, amount, message));
        } catch (DomainException e) {
            failed(sourceAccountId, e.code());
            throw e;
        } catch (ApplicationException e) {
            failed(sourceAccountId, e.code());
            throw e;
        }
    }

    private void failed(AccountId sourceAccountId, String code) {
        AuditEntry entry = AuditEntry.of(AuditEvent.PIX_FAILED).detail(code);
        audit.recordIndependently(sourceAccountId == null ? entry : entry.account(sourceAccountId));
    }

    private PixTransfer doSend(CustomerId requester, AccountId sourceAccountId, String rawKey, BigDecimal amount,
            String message) {
        customerService.requireActive(requester);
        Account source = accountService.findOwned(requester, sourceAccountId);
        PixKey key = resolve(rawKey);
        Account destination = destinationOf(key);
        Customer sender = customerService.get(requester);
        Customer receiver = customers.findById(destination.customerId())
                .orElseThrow(() -> ApplicationException.notFound("Pix key"));

        Money money = new Money(amount, source.balance().currency());
        Instant now = time.now();
        PixTransfer pix = PixTransfer.create(source.id(), destination.id(), money, message, key.value(),
                PixMasks.name(sender.name()), PixMasks.name(receiver.name()), EndToEndId.generate(now, random), now);
        LimitUsage.Status limit = limitUsage.of(source.id(), LimitType.PIX, money.currency());

        PixService.Result result = domain.execute(pix, source, destination, limit.limit(), limit.usedToday(), now);

        accounts.save(source);
        accounts.save(destination);
        transactions.save(result.debit());
        transactions.save(result.credit());
        transfers.save(pix);
        audit.record(AuditEntry.of(AuditEvent.PIX_SENT).account(source.id()).transaction(result.debit().id())
                .detail(pix.endToEndId()));
        outbox.record("PixCompleted", "Pix", pix.id().toString(), Map.of(
                "pixId", pix.id().toString(), "sourceAccountId", source.id().toString(),
                "destinationAccountId", destination.id().toString(), "amount", money.amount().toPlainString(),
                "currency", money.currency().getCurrencyCode()));
        return pix;
    }

    // ------------------------------------------------------------------ histórico
    @Transactional(readOnly = true)
    public PageResult<Entry> history(CustomerId requester, int page, int size) {
        PageResult.validate(page, size);
        List<AccountId> mine = accountService.list(requester).stream().map(Account::id).toList();
        if (mine.isEmpty()) {
            return new PageResult<>(List.of(), page, size, 0);
        }
        PageResult<PixTransfer> result = transfers.findByAccounts(mine, page, size);
        Map<PixTransferId, BigDecimal> refunded = transfers.refundedAmounts(result.items().stream().map(PixTransfer::id).toList());
        Instant now = time.now();
        List<Entry> entries = result.items().stream().map(p -> entryOf(p, mine, refunded.get(p.id()), now)).toList();
        return new PageResult<>(entries, result.page(), result.size(), result.totalElements());
    }

    /** Um Pix (enviado ou recebido por mim), com o que já foi devolvido e o que ainda pode ser. */
    @Transactional(readOnly = true)
    public Entry get(CustomerId requester, PixTransferId id) {
        List<AccountId> mine = accountService.list(requester).stream().map(Account::id).toList();
        PixTransfer pix = transfers.findById(id)
                .filter(p -> mine.contains(p.sourceAccountId()) || mine.contains(p.destinationAccountId()))
                .orElseThrow(() -> ApplicationException.notFound("Pix"));
        return entryOf(pix, mine, transfers.refundedAmounts(List.of(pix.id())).get(pix.id()), time.now());
    }

    private Entry entryOf(PixTransfer p, List<AccountId> mine, BigDecimal refundedAmount, Instant now) {
        boolean sent = mine.contains(p.sourceAccountId());
        Money refunded = refundedAmount == null ? Money.zero(p.amount().currency()) : new Money(refundedAmount, p.amount().currency());
        // só quem RECEBEU pode devolver; para quem enviou (ou para uma devolução) o valor devolvível é zero
        Money refundable = sent ? Money.zero(p.amount().currency()) : p.refundable(refunded, now);
        return new Entry(p, sent, sent ? p.destinationName() : p.sourceName(), refunded, refundable);
    }

    // ------------------------------------------------------------------ devolução
    /** Devolve (parte de) um Pix recebido. Sem {@code amount}, devolve o que ainda resta. Roda como o envio: com retry. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PixTransfer refund(CustomerId requester, PixTransferId originalId, BigDecimal amount) {
        PixTransfer original = transfers.findById(originalId).orElse(null);
        try {
            return retry.execute(() -> doRefund(requester, originalId, amount));
        } catch (DomainException e) {
            failed(original == null ? null : original.destinationAccountId(), e.code());
            throw e;
        } catch (ApplicationException e) {
            failed(original == null ? null : original.destinationAccountId(), e.code());
            throw e;
        }
    }

    private PixTransfer doRefund(CustomerId requester, PixTransferId originalId, BigDecimal amount) {
        customerService.requireActive(requester);
        PixTransfer original = transfers.findById(originalId).orElseThrow(() -> ApplicationException.notFound("Pix"));
        // só o dono da conta que RECEBEU enxerga o Pix como devolvível; qualquer outro recebe 404 (sem vazar que existe)
        Account receiver = accountService.findOwned(requester, original.destinationAccountId());
        Account payer = accounts.findById(original.sourceAccountId()).orElseThrow(() -> ApplicationException.notFound("Pix"));

        BigDecimal alreadyRefunded = transfers.refundedAmounts(List.of(original.id())).get(original.id());
        Money refunded = alreadyRefunded == null ? Money.zero(original.amount().currency()) : new Money(alreadyRefunded, original.amount().currency());
        Instant now = time.now();
        Money money = amount == null ? original.refundable(refunded, now) : new Money(amount, original.amount().currency());
        PixTransfer refund = PixTransfer.refundOf(original, money, EndToEndId.generate(now, random), now);

        PixService.Result result = domain.refund(original, refund, receiver, payer, refunded, now);

        accounts.save(receiver);
        accounts.save(payer);
        transactions.save(result.debit());
        transactions.save(result.credit());
        transfers.save(refund);
        audit.record(AuditEntry.of(AuditEvent.PIX_REFUNDED).account(receiver.id()).transaction(result.debit().id())
                .detail(refund.endToEndId()));
        outbox.record("PixRefunded", "Pix", refund.id().toString(), Map.of(
                "pixId", refund.id().toString(), "refundOf", original.id().toString(),
                "sourceAccountId", receiver.id().toString(), "destinationAccountId", payer.id().toString(),
                "amount", money.amount().toPlainString(), "currency", money.currency().getCurrencyCode()));
        return refund;
    }

    // ------------------------------------------------------------------ internos
    /** Chave inexistente: 404 genérico (não diz se faltou a chave ou se a conta está indisponível). */
    private PixKey resolve(String rawKey) {
        for (PixKeyType.Candidate candidate : PixKeyType.candidates(rawKey)) {
            Optional<PixKey> found = keys.findByValue(candidate.value());
            if (found.isPresent()) {
                return found.get();
            }
        }
        throw ApplicationException.notFound("Pix key");
    }

    private Account destinationOf(PixKey key) {
        return accounts.findById(key.accountId()).orElseThrow(() -> ApplicationException.notFound("Pix key"));
    }
}
