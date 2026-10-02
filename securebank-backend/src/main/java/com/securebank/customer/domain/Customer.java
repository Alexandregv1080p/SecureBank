package com.securebank.customer.domain;

import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import java.time.Instant;

/** Cliente do banco. Aggregate root: todas as mudanças passam pelos métodos, nunca por setters. */
public final class Customer {

    private final CustomerId id;
    private final String name;
    private final Cpf document;
    private Email email;
    private Phone phone;
    private CustomerStatus status;
    private final Instant createdAt;
    private Instant updatedAt;

    private Customer(CustomerId id, String name, Cpf document, Email email, Phone phone, CustomerStatus status,
            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.document = document;
        this.email = email;
        this.phone = phone;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** Reconstitui um cliente já persistido (dados já validados na origem), sem passar pelas regras de criação. */
    public static Customer restore(CustomerId id, String name, Cpf document, Email email, Phone phone,
            CustomerStatus status, Instant createdAt, Instant updatedAt) {
        return new Customer(id, name, document, email, phone, status, createdAt, updatedAt);
    }

    public static Customer register(String name, Cpf document, Email email, Phone phone, Instant now) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.length() < 2 || trimmed.length() > 120) {
            throw new InvalidValueException("Name must have 2-120 characters");
        }
        if (document == null || email == null || phone == null || now == null) {
            throw new InvalidValueException("Document, email and phone are required");
        }
        return new Customer(CustomerId.newId(), trimmed, document, email, phone, CustomerStatus.ACTIVE, now, now);
    }

    public void updateContact(Email email, Phone phone, Instant now) {
        ensureActive();
        if (email == null || phone == null) {
            throw new InvalidValueException("Email and phone are required");
        }
        this.email = email;
        this.phone = phone;
        this.updatedAt = now;
    }

    public void block(Instant now) {
        transitionTo(CustomerStatus.BLOCKED, now);
    }

    public void suspend(Instant now) {
        transitionTo(CustomerStatus.SUSPENDED, now);
    }

    public void reactivate(Instant now) {
        transitionTo(CustomerStatus.ACTIVE, now);
    }

    public void close(Instant now) {
        transitionTo(CustomerStatus.CLOSED, now);
    }

    /** Guarda para operações que exigem cliente ativo (ex.: abrir conta). */
    public void ensureActive() {
        if (status != CustomerStatus.ACTIVE) {
            throw new CustomerNotActiveException(status);
        }
    }

    private void transitionTo(CustomerStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Customer", status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public CustomerId id() { return id; }
    public String name() { return name; }
    public Cpf document() { return document; }
    public Email email() { return email; }
    public Phone phone() { return phone; }
    public CustomerStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
