package com.securebank.transfer.application;

import com.securebank.account.application.AccountApplicationService;
import com.securebank.account.application.AccountRepository;
import com.securebank.account.domain.Account;
import com.securebank.account.domain.AccountNumber;
import com.securebank.account.domain.Branch;
import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.limit.application.LimitUsage;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.IdempotencyKey;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.TransferId;
import com.securebank.transaction.application.TransactionRepository;
import com.securebank.transfer.domain.Transfer;
import com.securebank.transfer.domain.TransferService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
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

    public TransferApplicationService(TransferRepository transfers, AccountApplicationService accountService,
            AccountRepository accounts, CustomerApplicationService customers, LimitUsage limitUsage,
            TransactionRepository transactions, BankTime time) {
        this.transfers = transfers;
        this.accountService = accountService;
        this.accounts = accounts;
        this.customers = customers;
        this.limitUsage = limitUsage;
        this.transactions = transactions;
        this.time = time;
    }

    /**
     * Débito na origem e crédito no destino numa única transação de banco. Se qualquer regra falhar, nada é gravado.
     * Duas requisições concorrentes sobre a mesma conta: a segunda a gravar perde na versão otimista (409).
     */
    public Transfer transfer(CustomerId requester, AccountId sourceAccountId, String destinationBranch,
            String destinationNumber, BigDecimal amount, String description, String idempotencyKey) {
        customers.requireActive(requester);
        Account source = accountService.findOwned(requester, sourceAccountId);
        IdempotencyKey key = new IdempotencyKey(idempotencyKey);
        // Fase 3: chave repetida é recusada. A Fase 5 passa a devolver o resultado anterior (replay).
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
