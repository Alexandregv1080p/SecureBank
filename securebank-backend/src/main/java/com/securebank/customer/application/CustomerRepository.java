package com.securebank.customer.application;

import com.securebank.customer.domain.Cpf;
import com.securebank.customer.domain.Customer;
import com.securebank.customer.domain.Email;
import com.securebank.shared.domain.CustomerId;
import java.util.Optional;

/** Porta de saída: o domínio/aplicação pedem, a infraestrutura (JPA) implementa. */
public interface CustomerRepository {

    Optional<Customer> findById(CustomerId id);

    boolean existsByDocument(Cpf document);

    boolean existsByEmail(Email email);

    void save(Customer customer);
}
