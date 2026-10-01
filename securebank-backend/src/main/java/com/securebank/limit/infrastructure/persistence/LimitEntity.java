package com.securebank.limit.infrastructure.persistence;

import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.Money;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "limits")
@IdClass(LimitEntity.Key.class)
class LimitEntity {

    /** Chave composta (conta, tipo). */
    static class Key implements Serializable {
        UUID accountId;
        LimitType type;

        Key() {}

        Key(UUID accountId, LimitType type) {
            this.accountId = accountId;
            this.type = type;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key && Objects.equals(accountId, key.accountId) && type == key.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(accountId, type);
        }
    }

    @Id UUID accountId;
    @Id @Enumerated(EnumType.STRING) LimitType type;
    BigDecimal perOperation;
    BigDecimal daily;
    String currency;
    Instant updatedAt;

    void apply(Limit limit) {
        accountId = limit.accountId().value();
        type = limit.type();
        perOperation = limit.perOperation().amount();
        daily = limit.daily().amount();
        currency = limit.perOperation().currency().getCurrencyCode();
        updatedAt = limit.updatedAt();
    }

    Limit toDomain() {
        Currency cur = Currency.getInstance(currency);
        return Limit.restore(new AccountId(accountId), type, new Money(perOperation, cur), new Money(daily, cur),
                updatedAt);
    }
}
