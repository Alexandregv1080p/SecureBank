package com.securebank.customer.infrastructure.persistence;

import com.securebank.customer.domain.Cpf;
import com.securebank.customer.domain.Customer;
import com.securebank.customer.domain.CustomerStatus;
import com.securebank.shared.domain.Email;
import com.securebank.customer.domain.Phone;
import com.securebank.shared.domain.CustomerId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Modelo de persistência: o domínio não conhece JPA, então o mapeamento mora aqui. */
@Entity
@Table(name = "customers")
class CustomerEntity {

    @Id UUID id;
    String name;
    String document;
    String email;
    String phone;
    @Enumerated(EnumType.STRING) CustomerStatus status;
    Instant createdAt;
    Instant updatedAt;
    @Version Long version;

    void apply(Customer customer) {
        id = customer.id().value();
        name = customer.name();
        document = customer.document().value();
        email = customer.email().value();
        phone = customer.phone().value();
        status = customer.status();
        createdAt = customer.createdAt();
        updatedAt = customer.updatedAt();
    }

    Customer toDomain() {
        return Customer.restore(new CustomerId(id), name, new Cpf(document), new Email(email), new Phone(phone),
                status, createdAt, updatedAt);
    }
}
