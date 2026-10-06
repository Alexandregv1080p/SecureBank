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
import com.securebank.pix.domain.PixCharge;
import com.securebank.pix.domain.PixChargeStatus;
import com.securebank.pix.domain.PixMasks;
import com.securebank.pix.domain.PixTransfer;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.application.TransactionalRetry;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.DomainException;
import com.securebank.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cobranças Pix (QR dinâmico). Quem cobra define valor, validade e conta; quem paga só vê o necessário para decidir
 * (valor, descrição, nome e CPF MASCARADOS do recebedor). O pagamento é de uso único: a versão otimista da cobrança e
 * o índice único do txid em pix_transfers garantem que dois pagadores simultâneos não paguem a mesma cobrança.
 */
@Service
@Transactional
public class PixChargeApplicationService {

    /** O que o pagador vê ao ler o QR. */
    public record View(PixCharge charge, PixChargeStatus status, String receiverName, String receiverDocument, boolean own) {}

    private final PixChargeRepository charges;
    private final PixApplicationService pix;
    private final AccountApplicationService accountService;
    private final AccountRepository accounts;
    private final CustomerApplicationService customerService;
    private final CustomerRepository customers;
    private final BankTime time;
    private final AuditService audit;
    private final TransactionalRetry retry;

    public PixChargeApplicationService(PixChargeRepository charges, PixApplicationService pix,
            AccountApplicationService accountService, AccountRepository accounts,
            CustomerApplicationService customerService, CustomerRepository customers, BankTime time,
            AuditService audit, TransactionalRetry retry) {
        this.charges = charges;
        this.pix = pix;
        this.accountService = accountService;
        this.accounts = accounts;
        this.customerService = customerService;
        this.customers = customers;
        this.time = time;
        this.audit = audit;
        this.retry = retry;
    }

    public PixCharge create(CustomerId requester, AccountId accountId, BigDecimal amount, String description,
            Integer expiresInMinutes) {
        customerService.requireActive(requester);
        Account account = accountService.findOwned(requester, accountId);
        Duration ttl = expiresInMinutes == null ? null : Duration.ofMinutes(expiresInMinutes);
        PixCharge charge = PixCharge.create(requester, account.id(), new Money(amount, account.balance().currency()),
                description, ttl, time.now());
        charges.save(charge);
        audit.record(AuditEntry.of(AuditEvent.PIX_CHARGE_CREATED).account(account.id()).detail(charge.txid()));
        return charge;
    }

    @Transactional(readOnly = true)
    public PageResult<PixCharge> list(CustomerId requester, int page, int size) {
        PageResult.validate(page, size);
        return charges.findByCustomer(requester, page, size);
    }

    /** Consulta para pagar: qualquer cliente que tenha o txid (o QR) vê a cobrança; txid desconhecido é 404. */
    @Transactional(readOnly = true)
    public View view(CustomerId requester, String txid) {
        PixCharge charge = find(txid);
        Customer receiver = customers.findById(charge.customerId()).orElseThrow(() -> ApplicationException.notFound("Pix charge"));
        return new View(charge, charge.statusAt(time.now()), PixMasks.name(receiver.name()), receiver.document().masked(),
                charge.isOwnedBy(requester));
    }

    public void cancel(CustomerId requester, String txid) {
        PixCharge charge = charges.findByTxid(txid).filter(c -> c.isOwnedBy(requester))
                .orElseThrow(() -> ApplicationException.notFound("Pix charge"));
        charge.cancel();
        charges.save(charge);
        audit.record(AuditEntry.of(AuditEvent.PIX_CHARGE_CANCELED).account(charge.accountId()).detail(charge.txid()));
    }

    /** Paga a cobrança com uma das minhas contas. Roda como o envio de Pix: sem transação própria, com retry em conflito. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PixTransfer pay(CustomerId requester, String txid, AccountId sourceAccountId) {
        try {
            return retry.execute(() -> doPay(requester, txid, sourceAccountId));
        } catch (DomainException e) {
            audit.recordIndependently(AuditEntry.of(AuditEvent.PIX_FAILED).account(sourceAccountId).detail(e.code()));
            throw e;
        } catch (ApplicationException e) {
            audit.recordIndependently(AuditEntry.of(AuditEvent.PIX_FAILED).account(sourceAccountId).detail(e.code()));
            throw e;
        }
    }

    private PixTransfer doPay(CustomerId requester, String txid, AccountId sourceAccountId) {
        customerService.requireActive(requester);
        Account source = accountService.findOwned(requester, sourceAccountId);
        PixCharge charge = find(txid);
        Instant now = time.now();
        charge.ensurePayable(now);
        Account destination = accounts.findById(charge.accountId()).orElseThrow(() -> ApplicationException.notFound("Pix charge"));

        PixTransfer paid = pix.settle(requester, source, destination, charge.amount(), charge.description(), charge.txid(),
                charge.txid(), now);
        charge.markPaid(paid.id(), now);
        charges.save(charge);
        return paid;
    }

    private PixCharge find(String txid) {
        if (!PixCharge.isValidTxid(txid)) {
            throw ApplicationException.notFound("Pix charge");
        }
        return charges.findByTxid(txid).orElseThrow(() -> ApplicationException.notFound("Pix charge"));
    }
}
