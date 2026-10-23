package com.securebank.credit.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Tabelas do crédito (ver V18). Cada entidade é uma linha; as regras ficam no domínio. */
final class CreditEntities {

    private CreditEntities() {}

    @Entity(name = "CreditTermsEntity")
    @Table(name = "credit_terms")
    static class Terms {
        @Id UUID cardId;
        BigDecimal creditLimit;
        String currency;
        int dueDay;
        java.time.LocalDate billedOn;
        @Version Long version;
    }

    @Entity(name = "CreditPurchaseEntity")
    @Table(name = "credit_purchases")
    static class Purchase {
        @Id UUID id;
        UUID cardId;
        String merchant;
        BigDecimal totalAmount;
        String currency;
        int installments;
        Instant createdAt;
        String kind;
        UUID madeWith;
    }

    @Entity(name = "CreditInstallmentEntity")
    @Table(name = "credit_installments")
    static class Installment {
        @Id UUID id;
        UUID purchaseId;
        UUID cardId;
        int number;
        BigDecimal amount;
        String invoiceMonth;
    }

    @Entity(name = "CreditPaymentEntity")
    @Table(name = "credit_payments")
    static class Payment {
        @Id UUID id;
        UUID cardId;
        String invoiceMonth;
        BigDecimal amount;
        String currency;
        UUID transactionId;
        String kind;
        Instant createdAt;
    }
}
