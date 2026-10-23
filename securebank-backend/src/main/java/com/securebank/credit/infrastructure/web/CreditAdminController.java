package com.securebank.credit.infrastructure.web;

import com.securebank.credit.application.CreditApplicationService;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.infrastructure.web.MoneyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Equipe: ver os cartões de crédito de um cliente e decidir o limite (até R$ 100.000,00, nunca abaixo da dívida). */
@RestController
@RequestMapping("/api/v1/admin")
class CreditAdminController {

    record LimitRequest(@NotNull @DecimalMin("100.00") @Digits(integer = 9, fraction = 2) BigDecimal limit) {}

    record CreditCardView(UUID id, UUID accountId, String last4, String status, int dueDay, MoneyResponse creditLimit,
            MoneyResponse used, MoneyResponse available, Instant createdAt) {}

    record LimitView(UUID cardId, MoneyResponse creditLimit) {}

    private final CreditApplicationService credit;

    CreditAdminController(CreditApplicationService credit) {
        this.credit = credit;
    }

    @GetMapping("/customers/{id}/credit-cards")
    List<CreditCardView> creditCards(@PathVariable String id) {
        return credit.creditCardsOf(CustomerId.of(id)).stream()
                .map(v -> new CreditCardView(v.card().id().value(), v.card().accountId().value(), v.card().last4(), v.card().status().name(),
                        v.terms().dueDay(), MoneyResponse.of(v.terms().limit()), MoneyResponse.of(v.used()), MoneyResponse.of(v.available()),
                        v.card().createdAt()))
                .toList();
    }

    @PutMapping("/credit/cards/{id}/limit")
    LimitView changeLimit(@PathVariable String id, @Valid @RequestBody LimitRequest request) {
        return new LimitView(CardId.of(id).value(), MoneyResponse.of(credit.changeLimitAsStaff(CardId.of(id), request.limit())));
    }
}
