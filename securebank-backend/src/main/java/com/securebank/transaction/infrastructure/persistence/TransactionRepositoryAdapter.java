package com.securebank.transaction.infrastructure.persistence;

import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.application.TransactionRepository;
import com.securebank.transaction.domain.Transaction;
import com.securebank.transaction.domain.TransactionDirection;
import com.securebank.transaction.domain.TransactionStatus;
import com.securebank.transaction.domain.TransactionType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
class TransactionRepositoryAdapter implements TransactionRepository {

    private static final String RANGE = " from TransactionEntity t"
            + " where t.accountId = :account and t.createdAt >= :from and t.createdAt < :to";

    private final EntityManager em;

    TransactionRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    /** O livro-razão é append-only: só insere (o banco também recusa UPDATE de colunas e DELETE). */
    @Override
    public void save(Transaction transaction) {
        em.persist(TransactionEntity.of(transaction));
    }

    @Override
    public PageResult<Transaction> findStatement(AccountId accountId, Instant from, Instant to, int page, int size) {
        List<Transaction> items = em
                .createQuery("select t" + RANGE + " order by t.createdAt desc, t.id desc", TransactionEntity.class)
                .setParameter("account", accountId.value())
                .setParameter("from", from)
                .setParameter("to", to)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList().stream().map(TransactionEntity::toDomain).toList();
        long total = em.createQuery("select count(t)" + RANGE, Long.class)
                .setParameter("account", accountId.value())
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return new PageResult<>(items, page, size, total);
    }

    @Override
    public Money sumCompletedDebitsSince(AccountId accountId, TransactionType type, Instant since,
            Currency currency) {
        BigDecimal sum = em.createQuery("select sum(t.amount) from TransactionEntity t"
                        + " where t.accountId = :account and t.type = :type and t.direction = :direction"
                        + " and t.status = :status and t.createdAt >= :since", BigDecimal.class)
                .setParameter("account", accountId.value())
                .setParameter("type", type)
                .setParameter("direction", TransactionDirection.DEBIT)
                .setParameter("status", TransactionStatus.COMPLETED)
                .setParameter("since", since)
                .getSingleResult();
        return sum == null ? Money.zero(currency) : new Money(sum, currency);
    }
}
