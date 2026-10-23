package com.securebank.card.infrastructure.persistence;

import com.securebank.card.application.CardRepository;
import com.securebank.card.domain.Card;
import com.securebank.card.domain.CardPurchase;
import com.securebank.card.domain.CardStatus;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.CustomerId;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class CardRepositoryAdapter implements CardRepository {

    private final EntityManager em;

    CardRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Card> findById(CardId id) {
        return Optional.ofNullable(em.find(CardEntity.class, id.value())).map(CardEntity::toDomain);
    }

    @Override
    public List<Card> findOpenByCustomer(CustomerId customerId) {
        return em.createQuery("select c from CardEntity c where c.customerId = :customer and c.status <> :canceled"
                        + " order by c.createdAt, c.id", CardEntity.class)
                .setParameter("customer", customerId.value())
                .setParameter("canceled", CardStatus.CANCELED)
                .getResultList().stream().map(CardEntity::toDomain).toList();
    }

    @Override
    public long countOpenByAccount(AccountId accountId) {
        return em.createQuery("select count(c) from CardEntity c where c.accountId = :account and c.status <> :canceled",
                        Long.class)
                .setParameter("account", accountId.value())
                .setParameter("canceled", CardStatus.CANCELED)
                .getSingleResult();
    }

    @Override
    public void insert(Card card, String encryptedNumber, String numberHash) {
        CardEntity entity = new CardEntity();
        entity.apply(card);
        entity.numberEncrypted = encryptedNumber;
        entity.numberFingerprint = numberHash;
        em.persist(entity);
    }

    @Override
    public void save(Card card) {
        CardEntity entity = em.find(CardEntity.class, card.id().value());
        if (entity == null) {
            throw new IllegalStateException("Card " + card.id() + " does not exist; use insert for new cards");
        }
        entity.apply(card);
    }

    @Override
    public Optional<String> encryptedNumber(CardId id) {
        return Optional.ofNullable(em.find(CardEntity.class, id.value())).map(e -> e.numberEncrypted);
    }

    @Override
    public void save(CardPurchase purchase) {
        CardPurchaseEntity entity = new CardPurchaseEntity();
        entity.apply(purchase);
        em.persist(entity);
    }

    @Override
    public PageResult<CardPurchase> findPurchases(CardId cardId, int page, int size) {
        List<CardPurchase> items = em
                .createQuery("select p from CardPurchaseEntity p where p.cardId = :card order by p.createdAt desc, p.id desc",
                        CardPurchaseEntity.class)
                .setParameter("card", cardId.value())
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList().stream().map(CardPurchaseEntity::toDomain).toList();
        long total = em.createQuery("select count(p) from CardPurchaseEntity p where p.cardId = :card", Long.class)
                .setParameter("card", cardId.value()).getSingleResult();
        return new PageResult<>(items, page, size, total);
    }
}
