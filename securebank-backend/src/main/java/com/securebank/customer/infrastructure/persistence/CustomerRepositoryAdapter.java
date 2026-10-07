package com.securebank.customer.infrastructure.persistence;

import com.securebank.customer.application.CustomerRepository;
import com.securebank.customer.domain.Cpf;
import com.securebank.customer.domain.Customer;
import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.application.PageResult;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
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

    @Override
    public PageResult<Customer> search(SearchField field, String value, int page, int size) {
        String like = "lower(c.%s) like :v escape '\\'";
        String where = switch (field) {
            case DOCUMENT -> "c.document = :v";
            case NAME -> like.formatted("name");
            case EMAIL -> like.formatted("email");
            case PHONE -> like.formatted("phone");
            case NAME_OR_EMAIL -> "(" + like.formatted("name") + " or " + like.formatted("email") + ")";
        };
        String param = field == SearchField.DOCUMENT ? value : "%" + escapeLike(value.toLowerCase(Locale.ROOT)) + "%";
        List<Customer> items = em.createQuery("select c from CustomerEntity c where " + where
                        + " order by c.name, c.id", CustomerEntity.class)
                .setParameter("v", param).setFirstResult(page * size).setMaxResults(size)
                .getResultList().stream().map(CustomerEntity::toDomain).toList();
        long total = em.createQuery("select count(c) from CustomerEntity c where " + where, Long.class)
                .setParameter("v", param).getSingleResult();
        return new PageResult<>(items, page, size, total);
    }

    /** % e _ digitados pela equipe valem como texto, não como curinga (nem uma busca "%%" varre a base inteira). */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private boolean exists(String field, String value) {
        return em.createQuery("select count(c) from CustomerEntity c where c." + field + " = :v", Long.class)
                .setParameter("v", value).getSingleResult() > 0;
    }
}
