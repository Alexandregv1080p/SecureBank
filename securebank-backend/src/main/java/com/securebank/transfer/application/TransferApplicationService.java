package com.securebank.transfer.application;

import com.securebank.account.application.AccountApplicationService;
import com.securebank.account.application.AccountRepository;
import com.securebank.account.domain.Account;
import com.securebank.account.domain.AccountNumber;
import com.securebank.account.domain.Branch;
import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.limit.application.LimitUsage;
import com.securebank.limit.domain.LimitType;
import com.securebank.outbox.application.OutboxService;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.application.TransactionalRetry;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.DomainException;
import com.securebank.shared.domain.IdempotencyKey;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.TransferId;
import com.securebank.transaction.application.TransactionRepository;
import com.securebank.transfer.domain.Transfer;
import com.securebank.transfer.domain.TransferService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TransferApplicationService {

    private final TransferService domain = new TransferService();

    private final TransferRepository transfers;
    private final AccountApplicationService accountService;
    private final AccountRepository accounts;
    private final CustomerApplicationService customers;
    private final LimitUsage limitUsage;
    private final TransactionRepository transactions;
    private final BankTime time;
    private final AuditService audit;
    private final OutboxService outbox;
    private final TransactionalRetry retry;

    public TransferApplicationService(TransferRepository transfers, AccountApplicationService accountService,
            AccountRepository accounts, CustomerApplicationService customers, LimitUsage limitUsage,
            TransactionRepository transactions, BankTime time, AuditService audit,
            OutboxService outbox, TransactionalRetry retry) {
        this.transfers = transfers;
        this.accountService = accountService;
        this.accounts = accounts;
        this.customers = customers;
        this.limitUsage = limitUsage;
        this.transactions = transactions;
        this.time = time;
        this.audit = audit;
        this.outbox = outbox;
        this.retry = retry;
    }

    /**
     * Débito na origem e crédito no destino numa única transação de banco; o evento TRANSFER_CREATED entra nela.
     * Se uma regra recusar, a transação desfaz tudo e o TRANSFER_FAILED é gravado depois, numa transação à parte
     * (por isso este método roda sem transação e abre a sua própria, repetida em caso de conflito de versão).
     * Duas requisições concorrentes sobre a mesma conta: a segunda a gravar perde na versão otimista (409).
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Transfer transfer(CustomerId requester, AccountId sourceAccountId, String destinationBranch,
            String destinationNumber, BigDecimal amount, String description, String idempotencyKey) {
        try {
            return retry.execute(() -> doTransfer(requester, sourceAccountId, destinationBranch, destinationNumber,
                    amount, description, idempotencyKey));
        } catch (DomainException e) {
            failed(sourceAccountId, e.code());
            throw e;
        } catch (ApplicationException e) {
            failed(sourceAccountId, e.code());
            throw e;
        }
    }

    private void failed(AccountId sourceAccountId, String code) {
        audit.recordIndependently(AuditEntry.of(AuditEvent.TRANSFER_FAILED).account(sourceAccountId).detail(code));
        outbox.recordIndependently("TransferFailed", "Account", sourceAccountId.toString(),
                Map.of("sourceAccountId", sourceAccountId.toString(), "reason", code));
    }

    private Transfer doTransfer(CustomerId requester, AccountId sourceAccountId, String destinationBranch,
            String destinationNumber, BigDecimal amount, String description, String idempotencyKey) {
        customers.requireActive(requester);
        Account source = accountService.findOwned(requester, sourceAccountId);
        IdempotencyKey key = new IdempotencyKey(idempotencyKey);
        // Fase 3/4: chave repetida é recusada. A Fase 5 passa a devolver o resultado anterior (replay).
        if (transfers.existsBySourceAndKey(source.id(), key)) {
            throw ApplicationException.conflict("IDEMPOTENCY_KEY_IN_USE",
                    "A transfer with this Idempotency-Key already exists");
        }
        Account destination = accounts
                .findByBranchAndNumber(new Branch(destinationBranch), new AccountNumber(destinationNumber))
                .orElseThrow(() -> ApplicationException.notFound("Destination account"));

        Money money = new Money(amount, source.balance().currency());
        Instant now = time.now();
        Transfer transfer = Transfer.request(source.id(), destination.id(), money, description, key, now);
        LimitUsage.Status limit = limitUsage.of(source.id(), LimitType.TRANSFER, money.currency());

        TransferService.Result result = domain.execute(transfer, source, destination, limit.limit(),
                limit.usedToday(), now);

        accounts.save(source);
        accounts.save(destination);
        transactions.save(result.debit());
        transactions.save(result.credit());
        transfers.save(transfer);
        audit.record(AuditEntry.of(AuditEvent.TRANSFER_CREATED).account(source.id())
                .transaction(result.debit().id()).detail(transfer.id().toString()));
        outbox.record("TransferCompleted", "Transfer", transfer.id().toString(), Map.of(
                "transferId", transfer.id().toString(), "sourceAccountId", source.id().toString(),
                "destinationAccountId", destination.id().toString(), "amount", money.amount().toPlainString(),
                "currency", money.currency().getCurrencyCode()));
        return transfer;
    }

    @Transactional(readOnly = true)
    public Transfer get(CustomerId requester, TransferId id) {
        Transfer transfer = transfers.findById(id).orElseThrow(() -> ApplicationException.notFound("Transfer"));
        accountService.findOwned(requester, transfer.sourceAccountId()); // 404 se a origem não é do cliente
        return transfer;
    }

    @Transactional(readOnly = true)
    public PageResult<Transfer> list(CustomerId requester, int page, int size) {
        PageResult.validate(page, size);
        List<AccountId> ids = accountService.list(requester).stream().map(Account::id).toList();
        if (ids.isEmpty()) {
            return new PageResult<>(List.of(), page, size, 0);
        }
        return transfers.findBySourceAccounts(ids, page, size);
    }
}
