package com.securebank.fx.infrastructure.persistence;

import com.securebank.fx.domain.FxOperation;
import com.securebank.fx.domain.FxRate;
import com.securebank.fx.domain.FxSide;
import com.securebank.fx.domain.FxWallet;
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

final class FxEntities {

    private FxEntities() {}

    @Entity(name = "FxRateEntity")
    @Table(name = "fx_rates")
    static class RateEntity {
        @Id String currency;
        BigDecimal midRate;
        BigDecimal spread;
        Instant updatedAt;

        FxRate toDomain() {
            return new FxRate(Currency.getInstance(currency), midRate, spread, updatedAt);
        }
    }

    @Entity(name = "FxWalletEntity")
    @Table(name = "fx_wallets")
    static class WalletEntity {
        @Id UUID id;
        UUID customerId;
        String currency;
        BigDecimal balance;
        Instant updatedAt;
        /** Controle otimista: dois movimentos simultâneos na mesma carteira não passam os dois. */
        @Version Long version;

        void apply(FxWallet w) {
            id = w.id();
            customerId = w.customerId().value();
            currency = w.balance().currency().getCurrencyCode();
            balance = w.balance().amount();
            updatedAt = w.updatedAt();
        }

        FxWallet toDomain() {
            return FxWallet.restore(id, new CustomerId(customerId), new Money(balance, Currency.getInstance(currency)),
                    updatedAt);
        }
    }

    @Entity(name = "FxOperationEntity")
    @Table(name = "fx_operations")
    static class OperationEntity {
        @Id UUID id;
        UUID customerId;
        UUID accountId;
        @Enumerated(EnumType.STRING) FxSide side;
        String currency;
        BigDecimal foreignAmount;
        BigDecimal rate;
        BigDecimal brlAmount;
        Instant createdAt;

        static OperationEntity of(FxOperation o) {
            OperationEntity e = new OperationEntity();
            e.id = o.id();
            e.customerId = o.customerId().value();
            e.accountId = o.accountId().value();
            e.side = o.side();
            e.currency = o.foreignAmount().currency().getCurrencyCode();
            e.foreignAmount = o.foreignAmount().amount();
            e.rate = o.rate();
            e.brlAmount = o.brlAmount().amount();
            e.createdAt = o.createdAt();
            return e;
        }

        FxOperation toDomain() {
            return new FxOperation(id, new CustomerId(customerId), new AccountId(accountId), side,
                    new Money(foreignAmount, Currency.getInstance(currency)), rate, new Money(brlAmount, Money.BRL),
                    createdAt);
        }
    }
}
