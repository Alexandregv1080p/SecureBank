package com.securebank.investment.infrastructure.persistence;

import com.securebank.investment.domain.Investment;
import com.securebank.investment.domain.InvestmentStatus;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvestmentId;
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
@Table(name = "investments")
class InvestmentEntity {

    @Id UUID id;
    UUID customerId;
    UUID accountId;
    String productCode;
    String productName;
    BigDecimal principal;
    String currency;
    BigDecimal annualRate;
    Integer termDays;
    Instant appliedAt;
    Instant maturesAt;
    @Enumerated(EnumType.STRING) InvestmentStatus status;
    Instant redeemedAt;
    BigDecimal redeemedGross;
    BigDecimal redeemedTax;
    /** Controle otimista: dois resgates simultâneos da mesma aplicação não passam os dois. */
    @Version Long version;

    void apply(Investment i) {
        id = i.id().value();
        customerId = i.customerId().value();
        accountId = i.accountId().value();
        productCode = i.productCode();
        productName = i.productName();
        principal = i.principal().amount();
        currency = i.principal().currency().getCurrencyCode();
        annualRate = i.annualRate();
        termDays = i.termDays();
        appliedAt = i.appliedAt();
        maturesAt = i.maturesAt();
        status = i.status();
        redeemedAt = i.redeemedAt();
        redeemedGross = i.redeemedGross() == null ? null : i.redeemedGross().amount();
        redeemedTax = i.redeemedTax() == null ? null : i.redeemedTax().amount();
    }

    Investment toDomain() {
        Currency cur = Currency.getInstance(currency);
        return Investment.restore(new InvestmentId(id), new CustomerId(customerId), new AccountId(accountId),
                productCode, productName, new Money(principal, cur), annualRate, termDays, appliedAt, maturesAt, status,
                redeemedAt, redeemedGross == null ? null : new Money(redeemedGross, cur),
                redeemedTax == null ? null : new Money(redeemedTax, cur));
    }
}
