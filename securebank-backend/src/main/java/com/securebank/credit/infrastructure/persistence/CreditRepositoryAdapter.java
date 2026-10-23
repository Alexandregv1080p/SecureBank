package com.securebank.credit.infrastructure.persistence;

import com.securebank.credit.application.CreditRepository;
import com.securebank.credit.domain.CreditPurchase;
import com.securebank.credit.domain.CreditTerms;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.TransactionId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class CreditRepositoryAdapter implements CreditRepository {

    private final EntityManager em;

    CreditRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<CreditTerms> terms(CardId cardId) {
        return Optional.ofNullable(em.find(CreditEntities.Terms.class, cardId.value())).map(CreditRepositoryAdapter::toDomain);
    }

    @Override
    public Optional<CreditTerms> lockTerms(CardId cardId) {
        return Optional.ofNullable(em.find(CreditEntities.Terms.class, cardId.value(), LockModeType.PESSIMISTIC_WRITE))
                .map(CreditRepositoryAdapter::toDomain);
    }

    @Override
    public void insert(CreditTerms terms) {
        CreditEntities.Terms entity = new CreditEntities.Terms();
        entity.cardId = terms.cardId().value();
        entity.creditLimit = terms.limit().amount();
        entity.currency = terms.limit().currency().getCurrencyCode();
        entity.dueDay = terms.dueDay();
        em.persist(entity);
    }

    @Override
    public void save(CreditPurchase purchase) {
        CreditEntities.Purchase entity = new CreditEntities.Purchase();
        entity.id = purchase.id();
        entity.cardId = purchase.cardId().value();
        entity.merchant = purchase.merchant();
        entity.totalAmount = purchase.total().amount();
        entity.currency = purchase.total().currency().getCurrencyCode();
        entity.installments = purchase.installments();
        entity.createdAt = purchase.createdAt();
        em.persist(entity);
        for (CreditPurchase.Installment i : purchase.plan()) {
            CreditEntities.Installment row = new CreditEntities.Installment();
            row.id = i.id();
            row.purchaseId = purchase.id();
            row.cardId = purchase.cardId().value();
            row.number = i.number();
            row.amount = i.amount().amount();
            row.invoiceMonth = i.invoiceMonth().toString();
            em.persist(row);
        }
    }

    @Override
    public Map<YearMonth, Totals> totals(CardId cardId, Currency currency) {
        Map<YearMonth, BigDecimal> owed = sums("select i.invoiceMonth, sum(i.amount) from CreditInstallmentEntity i"
                + " where i.cardId = :card group by i.invoiceMonth", cardId);
        Map<YearMonth, BigDecimal> paid = sums("select p.invoiceMonth, sum(p.amount) from CreditPaymentEntity p"
                + " where p.cardId = :card group by p.invoiceMonth", cardId);
        Map<YearMonth, BigDecimal> fees = new HashMap<>();
        for (Object raw : em.createNativeQuery("select invoice_month, sum(amount) from credit_charges where card_id = :card"
                        + " group by invoice_month")
                .setParameter("card", cardId.value()).getResultList()) {
            Object[] row = (Object[]) raw;
            fees.put(YearMonth.parse((String) row[0]), (BigDecimal) row[1]);
        }
        java.util.Set<YearMonth> months = new java.util.HashSet<>(owed.keySet());
        months.addAll(paid.keySet());
        months.addAll(fees.keySet());
        Map<YearMonth, Totals> result = new HashMap<>();
        for (YearMonth month : months) {
            result.put(month, new Totals(new Money(owed.getOrDefault(month, BigDecimal.ZERO), currency),
                    new Money(fees.getOrDefault(month, BigDecimal.ZERO), currency),
                    new Money(paid.getOrDefault(month, BigDecimal.ZERO), currency)));
        }
        return result;
    }

    private Map<YearMonth, BigDecimal> sums(String jpql, CardId cardId) {
        Map<YearMonth, BigDecimal> sums = new HashMap<>();
        for (Object[] row : em.createQuery(jpql, Object[].class).setParameter("card", cardId.value()).getResultList()) {
            sums.put(YearMonth.parse((String) row[0]), (BigDecimal) row[1]);
        }
        return sums;
    }

    @Override
    public List<Charge> charges(CardId cardId, YearMonth month, Currency currency) {
        List<Charge> result = new java.util.ArrayList<>();
        for (Object raw : em.createNativeQuery("select kind, sum(amount) from credit_charges where card_id = :card"
                        + " and invoice_month = :month group by kind order by kind")
                .setParameter("card", cardId.value()).setParameter("month", month.toString()).getResultList()) {
            Object[] row = (Object[]) raw;
            result.add(new Charge((String) row[0], new Money((BigDecimal) row[1], currency)));
        }
        return result;
    }

    @Override
    public boolean hasCharge(CardId cardId, YearMonth month, String kind) {
        return ((Number) em.createNativeQuery("select count(*) from credit_charges where card_id = :card and invoice_month = :month"
                        + " and kind = :kind")
                .setParameter("card", cardId.value()).setParameter("month", month.toString()).setParameter("kind", kind)
                .getSingleResult()).longValue() > 0;
    }

    @Override
    public Optional<java.time.LocalDate> lastInterestDate(CardId cardId, YearMonth month) {
        Object last = em.createNativeQuery("select max(accrued_on) from credit_charges where card_id = :card and invoice_month = :month"
                        + " and kind = 'INTEREST'")
                .setParameter("card", cardId.value()).setParameter("month", month.toString()).getSingleResult();
        return Optional.ofNullable(last).map(d -> d instanceof java.sql.Date sql ? sql.toLocalDate() : (java.time.LocalDate) d);
    }

    @Override
    public boolean saveCharge(CardId cardId, YearMonth month, String kind, Money amount, java.time.LocalDate accruedOn, Instant now) {
        return em.createNativeQuery("insert into credit_charges (id, card_id, invoice_month, kind, amount, currency, accrued_on, created_at)"
                        + " values (:id, :card, :month, :kind, :amount, :currency, :on, :now) on conflict do nothing")
                .setParameter("id", UUID.randomUUID()).setParameter("card", cardId.value()).setParameter("month", month.toString())
                .setParameter("kind", kind).setParameter("amount", amount.amount())
                .setParameter("currency", amount.currency().getCurrencyCode()).setParameter("on", accruedOn).setParameter("now", now)
                .executeUpdate() > 0;
    }

    @Override
    public boolean markNotice(CardId cardId, YearMonth month, String kind, java.time.LocalDate today) {
        return em.createNativeQuery("insert into credit_notices (card_id, invoice_month, kind, sent_on) values (:card, :month, :kind, :on)"
                        + " on conflict do nothing")
                .setParameter("card", cardId.value()).setParameter("month", month.toString()).setParameter("kind", kind)
                .setParameter("on", today).executeUpdate() > 0;
    }

    @Override
    public List<CardId> dueForBilling(java.time.LocalDate today, int limit) {
        return em.createQuery("select t.cardId from CreditTermsEntity t where t.billedOn is null or t.billedOn < :today order by t.cardId",
                        UUID.class)
                .setParameter("today", today).setMaxResults(limit).getResultList().stream().map(CardId::new).toList();
    }

    @Override
    public void markBilled(CardId cardId, java.time.LocalDate today) {
        CreditEntities.Terms terms = em.find(CreditEntities.Terms.class, cardId.value());
        if (terms != null) {
            terms.billedOn = today;
        }
    }

    @Override
    public List<Item> items(CardId cardId, YearMonth month, Currency currency) {
        return em.createQuery("select p.merchant, i.number, p.installments, i.amount, p.createdAt"
                        + " from CreditInstallmentEntity i, CreditPurchaseEntity p"
                        + " where i.purchaseId = p.id and i.cardId = :card and i.invoiceMonth = :month"
                        + " order by p.createdAt, i.number", Object[].class)
                .setParameter("card", cardId.value())
                .setParameter("month", month.toString())
                .getResultList().stream()
                .map(r -> new Item((String) r[0], (Integer) r[1], (Integer) r[2], new Money((BigDecimal) r[3], currency),
                        ((Instant) r[4]).atZone(BankTime.ZONE).toLocalDate()))
                .toList();
    }

    @Override
    public List<Payment> payments(CardId cardId, YearMonth month, Currency currency) {
        return em.createQuery("select p.amount, p.createdAt from CreditPaymentEntity p"
                        + " where p.cardId = :card and p.invoiceMonth = :month order by p.createdAt desc", Object[].class)
                .setParameter("card", cardId.value())
                .setParameter("month", month.toString())
                .getResultList().stream()
                .map(r -> new Payment(new Money((BigDecimal) r[0], currency), (Instant) r[1]))
                .toList();
    }

    @Override
    public void savePayment(CardId cardId, YearMonth month, Money amount, TransactionId transactionId, Instant now) {
        CreditEntities.Payment entity = new CreditEntities.Payment();
        entity.id = UUID.randomUUID();
        entity.cardId = cardId.value();
        entity.invoiceMonth = month.toString();
        entity.amount = amount.amount();
        entity.currency = amount.currency().getCurrencyCode();
        entity.transactionId = transactionId.value();
        entity.createdAt = now;
        em.persist(entity);
    }

    @Override
    public PageResult<CreditPurchase> purchases(CardId cardId, int page, int size, Currency currency) {
        List<CreditPurchase> items = em.createQuery("select p from CreditPurchaseEntity p where p.cardId = :card"
                        + " order by p.createdAt desc, p.id desc", CreditEntities.Purchase.class)
                .setParameter("card", cardId.value())
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList().stream()
                .map(p -> CreditPurchase.restore(p.id, new CardId(p.cardId), p.merchant,
                        new Money(p.totalAmount, Currency.getInstance(p.currency)), p.installments, p.createdAt))
                .toList();
        long total = em.createQuery("select count(p) from CreditPurchaseEntity p where p.cardId = :card", Long.class)
                .setParameter("card", cardId.value()).getSingleResult();
        return new PageResult<>(items, page, size, total);
    }

    private static CreditTerms toDomain(CreditEntities.Terms e) {
        return CreditTerms.restore(new CardId(e.cardId), new Money(e.creditLimit, Currency.getInstance(e.currency)), e.dueDay);
    }
}
