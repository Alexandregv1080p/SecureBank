package com.securebank.account.infrastructure.persistence;

import com.securebank.account.application.AccountRepository;
import com.securebank.account.domain.Account;
import com.securebank.account.domain.AccountNumber;
import com.securebank.account.domain.Branch;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class AccountRepositoryAdapter implements AccountRepository {

    private final EntityManager em;

    AccountRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Account> findById(AccountId id) {
        return Optional.ofNullable(em.find(AccountEntity.class, id.value())).map(AccountEntity::toDomain);
    }

    @Override
    public Optional<Account> findByBranchAndNumber(Branch branch, AccountNumber number) {
        return em.createQuery("select a from AccountEntity a where a.branch = :branch and a.accountNumber = :number",
                        AccountEntity.class)
                .setParameter("branch", branch.value())
                .setParameter("number", number.value())
                .getResultStream().findFirst().map(AccountEntity::toDomain);
    }

    @Override
    public List<Account> findByCustomer(CustomerId customerId) {
        return em.createQuery("select a from AccountEntity a where a.customerId = :customer order by a.createdAt, a.id",
                        AccountEntity.class)
                .setParameter("customer", customerId.value())
                .getResultList().stream().map(AccountEntity::toDomain).toList();
    }

    @Override
    public void save(Account account) {
        AccountEntity entity = em.find(AccountEntity.class, account.id().value());
        if (entity == null) {
            entity = new AccountEntity();
            entity.apply(account);
            em.persist(entity);
        } else {
            entity.apply(account); // entidade gerenciada na mesma transação do find: o @Version vale no flush
        }
    }
}
