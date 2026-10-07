package com.securebank.customer.application;

import com.securebank.customer.domain.Cpf;
import com.securebank.customer.domain.Customer;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.CustomerId;
import java.util.Optional;

/** Porta de saída: o domínio/aplicação pedem, a infraestrutura (JPA) implementa. */
public interface CustomerRepository {

    Optional<Customer> findById(CustomerId id);

    boolean existsByDocument(Cpf document);

    boolean existsByEmail(Email email);

    void save(Customer customer);

    /** Campo em que a equipe busca. CPF só por valor completo (nunca por prefixo: não dá para enumerar CPFs). */
    enum SearchField { NAME, EMAIL, NAME_OR_EMAIL, PHONE, DOCUMENT }

    /**
     * Busca da equipe, ordenada por nome. NAME/EMAIL/NAME_OR_EMAIL/PHONE: contém [value] (sem diferenciar maiúsculas; os curingas
     * % e _ valem como texto). DOCUMENT: igual.
     */
    PageResult<Customer> search(SearchField field, String value, int page, int size);
}
