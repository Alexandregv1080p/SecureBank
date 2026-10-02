package com.securebank.customer.application;

import com.securebank.customer.domain.Cpf;
import com.securebank.customer.domain.Customer;
import com.securebank.shared.domain.Email;
import com.securebank.customer.domain.Phone;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.domain.CustomerId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CustomerApplicationService {

    private final CustomerRepository customers;
    private final BankTime time;

    public CustomerApplicationService(CustomerRepository customers, BankTime time) {
        this.customers = customers;
        this.time = time;
    }

    public Customer register(String name, String document, String email, String phone) {
        Cpf cpf = new Cpf(document);
        Email mail = new Email(email);
        // Resposta genérica de propósito: não confirmar qual dado (CPF ou e-mail) já existe.
        if (customers.existsByDocument(cpf) || customers.existsByEmail(mail)) {
            throw ApplicationException.conflict("CUSTOMER_ALREADY_EXISTS", "Customer already registered");
        }
        Customer customer = Customer.register(name, cpf, mail, new Phone(phone), time.now());
        customers.save(customer);
        return customer;
    }

    @Transactional(readOnly = true)
    public Customer get(CustomerId id) {
        return customers.findById(id).orElseThrow(() -> ApplicationException.notFound("Customer"));
    }

    /** Atualização parcial: o que vier nulo permanece como está. */
    public Customer updateContact(CustomerId id, String email, String phone) {
        Customer customer = get(id);
        Email newEmail = email == null ? customer.email() : new Email(email);
        if (!newEmail.equals(customer.email()) && customers.existsByEmail(newEmail)) {
            throw ApplicationException.conflict("CUSTOMER_ALREADY_EXISTS", "Email already in use");
        }
        customer.updateContact(newEmail, phone == null ? customer.phone() : new Phone(phone), time.now());
        customers.save(customer);
        return customer;
    }

    /** Operações que movimentam dinheiro exigem cliente ativo (bloqueado/suspenso/encerrado não opera). */
    @Transactional(readOnly = true)
    public void requireActive(CustomerId id) {
        get(id).ensureActive();
    }
}
