package com.securebank.customer.infrastructure.persistence;

import com.securebank.customer.application.CustomerRepository;
import com.securebank.customer.domain.Cpf;
import com.securebank.customer.domain.Customer;
import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.CustomerId;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class CustomerRepositoryAdapter implements CustomerRepository {

    private final EntityManager em;

    CustomerRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Customer> findById(CustomerId id) {
        return Optional.ofNullable(em.find(CustomerEntity.class, id.value())).map(CustomerEntity::toDomain);
    }

    @Override
    public boolean existsByDocument(Cpf document) {
        return exists("document", document.value());
    }

    @Override
    public boolean existsByEmail(Email email) {
        return exists("email", email.value());
    }

    @Override
    public void save(Customer customer) {
        CustomerEntity entity = em.find(CustomerEntity.class, customer.id().value());
        if (entity == null) {
            entity = new CustomerEntity();
            entity.apply(customer);
            em.persist(entity);
        } else {
            entity.apply(customer); // entidade gerenciada: o @Version é conferido no flush
        }
    }

    private boolean exists(String field, String value) {
        return em.createQuery("select count(c) from CustomerEntity c where c." + field + " = :v", Long.class)
                .setParameter("v", value).getSingleResult() > 0;
    }
}
