package com.securebank.pix.infrastructure.persistence;

import com.securebank.pix.domain.PixKey;
import com.securebank.pix.domain.PixKeyType;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.PixKeyId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pix_keys")
class PixKeyEntity {

    @Id UUID id;
    UUID customerId;
    UUID accountId;
    @Enumerated(EnumType.STRING) PixKeyType type;
    String value;
    Instant createdAt;

    void apply(PixKey key) {
        id = key.id().value();
        customerId = key.customerId().value();
        accountId = key.accountId().value();
        type = key.type();
        value = key.value();
        createdAt = key.createdAt();
    }

    PixKey toDomain() {
        return PixKey.restore(new PixKeyId(id), new CustomerId(customerId), new AccountId(accountId), type, value,
                createdAt);
    }
}
