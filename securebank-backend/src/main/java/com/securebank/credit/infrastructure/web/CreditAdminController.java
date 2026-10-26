package com.securebank.credit.infrastructure.web;

import com.securebank.credit.application.CreditApplicationService;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.infrastructure.web.MoneyResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Equipe: ver os cartões de crédito de um cliente. Decidir o limite passa pelas alçadas (módulo approval). */
@RestController
@RequestMapping("/api/v1/admin")
class CreditAdminController {

    record CreditCardView(UUID id, UUID accountId, String last4, String status, int dueDay, MoneyResponse creditLimit,
            MoneyResponse used, MoneyResponse available, Instant createdAt) {}

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
}
