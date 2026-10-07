package com.securebank.customer.application;

import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.customer.application.CustomerRepository.SearchField;
import com.securebank.customer.domain.Customer;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.InvalidValueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Busca de clientes pela equipe (VIEW_CUSTOMER). É dado pessoal: cada busca entra na auditoria, mas só com o TIPO da
 * busca e quantos resultados deu, nunca o texto digitado. CPF só por valor completo.
 */
@Service
@Transactional
public class CustomerSearchService {

    static final int MIN_LENGTH = 3;

    private final CustomerRepository customers;
    private final AuditService audit;

    public CustomerSearchService(CustomerRepository customers, AuditService audit) {
        this.customers = customers;
        this.audit = audit;
    }

    public PageResult<Customer> search(String query, int page, int size) {
        PageResult.validate(page, size);
        String q = query == null ? "" : query.trim();
        if (q.length() < MIN_LENGTH) {
            throw new InvalidValueException("Search needs at least " + MIN_LENGTH + " characters");
        }
        String digits = q.replaceAll("\\D", "");
        boolean numeric = q.matches("[\\d\\s().+-]+");
        PageResult<Customer> result;
        String kind;
        if (q.contains("@")) {
            kind = "email";
            result = customers.search(SearchField.EMAIL, q, page, size);
        } else if (numeric && digits.length() == 11) {
            kind = "cpf";
            result = customers.search(SearchField.DOCUMENT, digits, page, size);
            if (result.totalElements() == 0) { // 11 dígitos também pode ser celular com DDD
                kind = "telefone";
                result = customers.search(SearchField.PHONE, digits, page, size);
            }
        } else if (numeric) {
            if (digits.length() < MIN_LENGTH) {
                throw new InvalidValueException("Search needs at least " + MIN_LENGTH + " digits");
            }
            kind = "telefone";
            result = customers.search(SearchField.PHONE, digits, page, size);
        } else {
            kind = "nome/e-mail";
            result = customers.search(SearchField.NAME_OR_EMAIL, q, page, size);
        }
        audit.record(AuditEntry.of(AuditEvent.CUSTOMER_SEARCHED).detail(kind + ": " + result.totalElements() + " resultado(s)"));
        return result;
    }
}
