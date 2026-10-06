package com.securebank.pix.infrastructure.persistence;

import com.securebank.pix.domain.PixCharge;
import com.securebank.pix.domain.PixChargeStatus;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PixTransferId;
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
@Table(name = "pix_charges")
class PixChargeEntity {

    @Id String txid;
    UUID customerId;
    UUID accountId;
    BigDecimal amount;
    String currency;
    String description;
    @Enumerated(EnumType.STRING) PixChargeStatus status;
    Instant expiresAt;
    Instant createdAt;
    Instant paidAt;
    UUID paidPixId;
    @Version Long version;

    void apply(PixCharge c) {
        txid = c.txid();
        customerId = c.customerId().value();
        accountId = c.accountId().value();
        amount = c.amount().amount();
        currency = c.amount().currency().getCurrencyCode();
        description = c.description();
        status = c.status();
        expiresAt = c.expiresAt();
        createdAt = c.createdAt();
        paidAt = c.paidAt();
        paidPixId = c.paidPixId() == null ? null : c.paidPixId().value();
    }

    PixCharge toDomain() {
        return PixCharge.restore(txid, new CustomerId(customerId), new AccountId(accountId),
                new Money(amount, Currency.getInstance(currency)), description, status, expiresAt, createdAt, paidAt,
                paidPixId == null ? null : new PixTransferId(paidPixId));
    }
}
