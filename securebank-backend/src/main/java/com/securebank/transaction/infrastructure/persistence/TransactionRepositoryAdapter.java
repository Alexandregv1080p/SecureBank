package com.securebank.transaction.infrastructure.persistence;

import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.application.StatementFilter;
import com.securebank.transaction.application.StatementTotal;
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
    public PageResult<Transaction> findStatement(AccountId accountId, Instant from, Instant to, StatementFilter filter,
            int page, int size) {
        String where = RANGE + (filter.category() == null ? "" : " and t.type in :types")
                + (filter.direction() == null ? "" : " and t.direction = :direction");
        var items = em.createQuery("select t" + where + " order by t.createdAt desc, t.id desc", TransactionEntity.class);
        var count = em.createQuery("select count(t)" + where, Long.class);
        for (var q : List.of(items, count)) {
            q.setParameter("account", accountId.value()).setParameter("from", from).setParameter("to", to);
            if (filter.category() != null) {
                q.setParameter("types", filter.category().types());
            }
            if (filter.direction() != null) {
                q.setParameter("direction", filter.direction());
            }
        }
        List<Transaction> content = items.setFirstResult(page * size).setMaxResults(size).getResultList().stream()
                .map(TransactionEntity::toDomain).toList();
        return new PageResult<>(content, page, size, count.getSingleResult());
    }

    @Override
    public List<StatementTotal> totals(AccountId accountId, Instant from, Instant to) {
        return em.createQuery("select t.type, t.direction, sum(t.amount)" + RANGE + " and t.status = :status"
                        + " group by t.type, t.direction", Object[].class)
                .setParameter("account", accountId.value())
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("status", TransactionStatus.COMPLETED)
                .getResultList().stream()
                .map(r -> new StatementTotal((TransactionType) r[0], (TransactionDirection) r[1], (BigDecimal) r[2]))
                .toList();
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
