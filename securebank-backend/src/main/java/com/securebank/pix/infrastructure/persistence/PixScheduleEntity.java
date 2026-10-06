package com.securebank.pix.infrastructure.persistence;

import com.securebank.pix.domain.PixSchedule;
import com.securebank.pix.domain.PixScheduleStatus;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PixScheduleId;
import com.securebank.shared.domain.PixTransferId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "pix_schedules")
class PixScheduleEntity {

    @Id UUID id;
    UUID customerId;
    UUID sourceAccountId;
    @Column(name = "pix_key") String pixKey;
    String destinationName;
    BigDecimal amount;
    String currency;
    String message;
    LocalDate scheduledFor;
    @Enumerated(EnumType.STRING) PixScheduleStatus status;
    String failureReason;
    UUID executedPixId;
    Instant createdAt;
    Instant updatedAt;
    @Version Long version;

    void apply(PixSchedule s) {
        id = s.id().value();
        customerId = s.customerId().value();
        sourceAccountId = s.sourceAccountId().value();
        pixKey = s.key();
        destinationName = s.destinationName();
        amount = s.amount().amount();
        currency = s.amount().currency().getCurrencyCode();
        message = s.message();
        scheduledFor = s.scheduledFor();
        status = s.status();
        failureReason = s.failureReason();
        executedPixId = s.executedPixId() == null ? null : s.executedPixId().value();
        createdAt = s.createdAt();
        updatedAt = s.updatedAt();
    }

    PixSchedule toDomain() {
        return PixSchedule.restore(new PixScheduleId(id), new CustomerId(customerId), new AccountId(sourceAccountId),
                pixKey, destinationName, new Money(amount, Currency.getInstance(currency)), message, scheduledFor, status,
                failureReason, executedPixId == null ? null : new PixTransferId(executedPixId), createdAt, updatedAt);
    }
}
