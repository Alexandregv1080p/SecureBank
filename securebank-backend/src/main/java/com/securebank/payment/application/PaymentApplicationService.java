package com.securebank.payment.application;

import com.securebank.account.application.AccountApplicationService;
import com.securebank.account.application.AccountRepository;
import com.securebank.account.domain.Account;
import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.limit.application.LimitUsage;
import com.securebank.limit.domain.LimitType;
import com.securebank.payment.domain.Payment;
import com.securebank.payment.domain.PaymentService;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.IdempotencyKey;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PaymentId;
import com.securebank.transaction.application.TransactionRepository;
import com.securebank.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentApplicationService {

    private final PaymentService domain = new PaymentService();

    private final PaymentRepository payments;
    private final AccountApplicationService accountService;
    private final AccountRepository accounts;
    private final CustomerApplicationService customers;
    private final LimitUsage limitUsage;
    private final TransactionRepository transactions;
    private final BankTime time;

    public PaymentApplicationService(PaymentRepository payments, AccountApplicationService accountService,
            AccountRepository accounts, CustomerApplicationService customers, LimitUsage limitUsage,
            TransactionRepository transactions, BankTime time) {
        this.payments = payments;
        this.accountService = accountService;
        this.accounts = accounts;
        this.customers = customers;
        this.limitUsage = limitUsage;
        this.transactions = transactions;
        this.time = time;
    }

    public Payment pay(CustomerId requester, AccountId accountId, BigDecimal amount, String barcode,
            String description, String idempotencyKey) {
        customers.requireActive(requester);
        Account account = accountService.findOwned(requester, accountId);
        IdempotencyKey key = new IdempotencyKey(idempotencyKey);
        // Fase 3: chave repetida é recusada. A Fase 5 passa a devolver o resultado anterior (replay).
        if (payments.existsByAccountAndKey(account.id(), key)) {
            throw ApplicationException.conflict("IDEMPOTENCY_KEY_IN_USE",
                    "A payment with this Idempotency-Key already exists");
        }

        Instant now = time.now();
        Money money = new Money(amount, account.balance().currency());
        Payment payment = Payment.create(account.id(), money, barcode, description, key, now);
        LimitUsage.Status limit = limitUsage.of(account.id(), LimitType.PAYMENT, money.currency());

        Transaction transaction = domain.execute(payment, account, limit.limit(), limit.usedToday(), now);

        accounts.save(account);
        transactions.save(transaction);
        payments.save(payment);
        return payment;
    }

    @Transactional(readOnly = true)
    public Payment get(CustomerId requester, PaymentId id) {
        Payment payment = payments.findById(id).orElseThrow(() -> ApplicationException.notFound("Payment"));
        accountService.findOwned(requester, payment.accountId());
        return payment;
    }

    @Transactional(readOnly = true)
    public PageResult<Payment> list(CustomerId requester, int page, int size) {
        PageResult.validate(page, size);
        List<AccountId> ids = accountService.list(requester).stream().map(Account::id).toList();
        if (ids.isEmpty()) {
            return new PageResult<>(List.of(), page, size, 0);
        }
        return payments.findByAccounts(ids, page, size);
    }
}
