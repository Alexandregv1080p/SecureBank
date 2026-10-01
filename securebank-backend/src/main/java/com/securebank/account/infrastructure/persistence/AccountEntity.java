package com.securebank.account.infrastructure.persistence;

import com.securebank.account.domain.Account;
import com.securebank.account.domain.AccountNumber;
import com.securebank.account.domain.AccountStatus;
import com.securebank.account.domain.AccountType;
import com.securebank.account.domain.Branch;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "accounts")
class AccountEntity {

    @Id UUID id;
    UUID customerId;
    String branch;
    String accountNumber;
    @Enumerated(EnumType.STRING) AccountType type;
    @Enumerated(EnumType.STRING) AccountStatus status;
    BigDecimal balance;
    String currency;
    Instant createdAt;
    Instant updatedAt;
    /** Controle otimista: duas escritas concorrentes no mesmo saldo não passam as duas (a perdedora recebe 409). */
    @Version Long version;

    void apply(Account account) {
        id = account.id().value();
        customerId = account.customerId().value();
        branch = account.branch().value();
        accountNumber = account.accountNumber().value();
        type = account.type();
        status = account.status();
        balance = account.balance().amount();
        currency = account.balance().currency().getCurrencyCode();
        createdAt = account.createdAt();
        updatedAt = account.updatedAt();
    }

    Account toDomain() {
        return Account.restore(new AccountId(id), new CustomerId(customerId), new AccountNumber(accountNumber),
                new Branch(branch), type, status, new Money(balance, Currency.getInstance(currency)), createdAt,
                updatedAt);
    }
}
