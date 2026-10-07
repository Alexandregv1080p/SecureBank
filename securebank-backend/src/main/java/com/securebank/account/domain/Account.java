package com.securebank.account.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.domain.Transaction;
import com.securebank.transaction.domain.TransactionDirection;
import com.securebank.transaction.domain.TransactionType;
import java.time.Instant;
import java.util.Currency;

/**
 * Conta bancária. Dona das invariantes do saldo: nunca negativo, só movimenta se ACTIVE, só na sua moeda,
 * só valor positivo. Cada movimentação devolve o {@link Transaction} correspondente, então saldo e lançamento
 * nascem juntos (e são persistidos na mesma transação de banco).
 */
public final class Account {

    private final AccountId id;
    private final CustomerId customerId;
    private final AccountNumber accountNumber;
    private final Branch branch;
    private final AccountType type;
    private AccountStatus status;
    private Money balance;
    private final Instant createdAt;
    private Instant updatedAt;

    private Account(AccountId id, CustomerId customerId, AccountNumber accountNumber, Branch branch, AccountType type,
            AccountStatus status, Money balance, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.accountNumber = accountNumber;
        this.branch = branch;
        this.type = type;
        this.status = status;
        this.balance = balance;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Account open(CustomerId customerId, AccountNumber accountNumber, Branch branch, AccountType type,
            Currency currency, Instant now) {
        if (customerId == null || accountNumber == null || branch == null || type == null || currency == null
                || now == null) {
            throw new InvalidValueException("Account requires customer, number, branch, type, currency and time");
        }
        return new Account(AccountId.newId(), customerId, accountNumber, branch, type, AccountStatus.ACTIVE,
                Money.zero(currency), now, now);
    }

    /** Reconstitui uma conta já persistida, sem passar pelas regras de abertura. */
    public static Account restore(AccountId id, CustomerId customerId, AccountNumber accountNumber, Branch branch,
            AccountType type, AccountStatus status, Money balance, Instant createdAt, Instant updatedAt) {
        return new Account(id, customerId, accountNumber, branch, type, status, balance, createdAt, updatedAt);
    }

    public Transaction deposit(Money amount, String reference, Instant now) {
        return credit(TransactionType.DEPOSIT, amount, reference, now);
    }

    public Transaction withdraw(Money amount, String reference, Instant now) {
        return debit(TransactionType.WITHDRAW, amount, reference, now);
    }

    public Transaction transferOut(Money amount, String reference, Instant now) {
        return debit(TransactionType.TRANSFER, amount, reference, now);
    }

    public Transaction transferIn(Money amount, String reference, Instant now) {
        return credit(TransactionType.TRANSFER, amount, reference, now);
    }

    public Transaction pay(Money amount, String reference, Instant now) {
        return debit(TransactionType.PAYMENT, amount, reference, now);
    }

    public Transaction refund(Money amount, String reference, Instant now) {
        return credit(TransactionType.REFUND, amount, reference, now);
    }

    /** Pix enviado: débito na conta de origem. */
    public Transaction pixOut(Money amount, String reference, Instant now) {
        return debit(TransactionType.PIX_OUT, amount, reference, now);
    }

    /** Pix recebido: crédito na conta de destino. */
    public Transaction pixIn(Money amount, String reference, Instant now) {
        return credit(TransactionType.PIX_IN, amount, reference, now);
    }

    /** Devolução de Pix paga por esta conta (que havia recebido o Pix original). */
    public Transaction pixReturnOut(Money amount, String reference, Instant now) {
        return debit(TransactionType.PIX_RETURN_OUT, amount, reference, now);
    }

    /** Devolução de Pix recebida por esta conta (que havia enviado o Pix original). */
    public Transaction pixReturnIn(Money amount, String reference, Instant now) {
        return credit(TransactionType.PIX_RETURN_IN, amount, reference, now);
    }

    /** Guarda dinheiro num porquinho: sai do saldo da conta (o porquinho recebe, na mesma transação). */
    public Transaction saveToPiggy(Money amount, String reference, Instant now) {
        return debit(TransactionType.PIGGY_IN, amount, reference, now);
    }

    /** Resgata dinheiro de um porquinho de volta para o saldo da conta. */
    public Transaction redeemFromPiggy(Money amount, String reference, Instant now) {
        return credit(TransactionType.PIGGY_OUT, amount, reference, now);
    }

    /** Aplica dinheiro em renda fixa: sai do saldo (a aplicação nasce na mesma transação). */
    public Transaction invest(Money amount, String reference, Instant now) {
        return debit(TransactionType.INVEST_OUT, amount, reference, now);
    }

    /** Resgate de uma aplicação: o líquido (principal + rendimento − IR) volta para o saldo. */
    public Transaction redeemInvestment(Money amount, String reference, Instant now) {
        return credit(TransactionType.INVEST_IN, amount, reference, now);
    }

    /** Pré-condições de um crédito. Públicas para que serviços de domínio validem as DUAS contas antes de mutar qualquer uma. */
    public void ensureCanCredit(Money amount) {
        ensureActive();
        requirePositive(amount);
        balance.requireSameCurrency(amount);
    }

    public void ensureCanDebit(Money amount) {
        ensureCanCredit(amount);
        if (balance.isLessThan(amount)) {
            throw new InsufficientFundsException();
        }
    }

    public void block(Instant now) {
        transitionTo(AccountStatus.BLOCKED, now);
    }

    public void unblock(Instant now) {
        transitionTo(AccountStatus.ACTIVE, now);
    }

    public void close(Instant now) {
        if (!balance.isZero()) {
            throw new AccountHasBalanceException();
        }
        transitionTo(AccountStatus.CLOSED, now);
    }

    /** Base da autorização por recurso (IDOR): a conta só é acessível pelo dono. */
    public boolean isOwnedBy(CustomerId customerId) {
        return this.customerId.equals(customerId);
    }

    private Transaction credit(TransactionType type, Money amount, String reference, Instant now) {
        ensureCanCredit(amount);
        balance = balance.plus(amount);
        updatedAt = now;
        return Transaction.completed(id, type, TransactionDirection.CREDIT, amount, balance, reference, now);
    }

    private Transaction debit(TransactionType type, Money amount, String reference, Instant now) {
        ensureCanDebit(amount);
        balance = balance.minus(amount);
        updatedAt = now;
        return Transaction.completed(id, type, TransactionDirection.DEBIT, amount, balance, reference, now);
    }

    private void ensureActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(status);
        }
    }

    private static void requirePositive(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
    }

    private void transitionTo(AccountStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Account", status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public AccountId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public AccountNumber accountNumber() { return accountNumber; }
    public Branch branch() { return branch; }
    public AccountType type() { return type; }
    public AccountStatus status() { return status; }
    public Money balance() { return balance; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
