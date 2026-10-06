package com.securebank.piggy.infrastructure.persistence;

import com.securebank.piggy.domain.Piggy;
import com.securebank.piggy.domain.PiggyStatus;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PiggyId;
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
@Table(name = "piggies")
class PiggyEntity {

    @Id UUID id;
    UUID customerId;
    UUID accountId;
    String name;
    BigDecimal goal;
    BigDecimal balance;
    String currency;
    @Enumerated(EnumType.STRING) PiggyStatus status;
    Instant createdAt;
    Instant updatedAt;
    /** Controle otimista: dois depósitos/resgates simultâneos no mesmo porquinho não passam os dois. */
    @Version Long version;

    void apply(Piggy piggy) {
        id = piggy.id().value();
        customerId = piggy.customerId().value();
        accountId = piggy.accountId().value();
        name = piggy.name();
        goal = piggy.goal() == null ? null : piggy.goal().amount();
        balance = piggy.balance().amount();
        currency = piggy.balance().currency().getCurrencyCode();
        status = piggy.status();
        createdAt = piggy.createdAt();
        updatedAt = piggy.updatedAt();
    }

    Piggy toDomain() {
        Currency cur = Currency.getInstance(currency);
        return Piggy.restore(new PiggyId(id), new CustomerId(customerId), new AccountId(accountId), name,
                goal == null ? null : new Money(goal, cur), new Money(balance, cur), status, createdAt, updatedAt);
    }
}
