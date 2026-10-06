package com.securebank.pix.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.PixKeyId;
import java.time.Instant;

/** Chave Pix: um apelido (CPF, e-mail, celular ou aleatória) que aponta para uma conta. O valor é único no banco. */
public final class PixKey {

    private final PixKeyId id;
    private final CustomerId customerId;
    private final AccountId accountId;
    private final PixKeyType type;
    private final String value;
    private final Instant createdAt;

    private PixKey(PixKeyId id, CustomerId customerId, AccountId accountId, PixKeyType type, String value,
            Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.accountId = accountId;
        this.type = type;
        this.value = value;
        this.createdAt = createdAt;
    }

    /** @param value já canônico (ver {@link PixKeyType#normalize}) */
    public static PixKey register(CustomerId customerId, AccountId accountId, PixKeyType type, String value,
            Instant now) {
        if (customerId == null || accountId == null || type == null || value == null || now == null) {
            throw new InvalidValueException("Pix key requires customer, account, type, value and time");
        }
        return new PixKey(PixKeyId.newId(), customerId, accountId, type, type.normalize(value), now);
    }

    public static PixKey restore(PixKeyId id, CustomerId customerId, AccountId accountId, PixKeyType type,
            String value, Instant createdAt) {
        return new PixKey(id, customerId, accountId, type, value, createdAt);
    }

    public boolean isOwnedBy(CustomerId customerId) {
        return this.customerId.equals(customerId);
    }

    public PixKeyId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public AccountId accountId() { return accountId; }
    public PixKeyType type() { return type; }
    public String value() { return value; }
    public Instant createdAt() { return createdAt; }
}
