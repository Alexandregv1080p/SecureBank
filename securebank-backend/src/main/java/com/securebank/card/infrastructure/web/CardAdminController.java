package com.securebank.card.infrastructure.web;

import com.securebank.card.application.CardApplicationService;
import com.securebank.card.domain.Card;
import com.securebank.card.domain.CardNumber;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.CustomerId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Equipe: ver os cartões de um cliente (nunca o número) e bloquear/desbloquear com motivo. Cancelar é decisão do cliente. */
@RestController
@RequestMapping("/api/v1/admin")
class CardAdminController {

    record ReasonRequest(@NotBlank @Size(min = Card.REASON_MIN, max = Card.REASON_MAX) String reason) {}

    record CardView(UUID id, UUID accountId, String kind, String holderName, String maskedNumber, String last4, String status,
            String statusReason, Instant createdAt) {

        static CardView of(Card c) {
            return new CardView(c.id().value(), c.accountId().value(), c.kind().name(), c.holderName(), CardNumber.masked(c.last4()),
                    c.last4(), c.status().name(), c.statusReason(), c.createdAt());
        }
    }

    private final CardApplicationService cards;

    CardAdminController(CardApplicationService cards) {
        this.cards = cards;
    }

    @GetMapping("/customers/{id}/cards")
    List<CardView> cardsOf(@PathVariable String id) {
        return cards.listOpenForStaff(CustomerId.of(id)).stream().map(CardView::of).toList();
    }

    @PostMapping("/cards/{id}/block")
    CardView block(@PathVariable String id, @Valid @RequestBody ReasonRequest request) {
        return CardView.of(cards.blockAsStaff(CardId.of(id), request.reason()));
    }

    @PostMapping("/cards/{id}/unblock")
    CardView unblock(@PathVariable String id) {
        return CardView.of(cards.unblockAsStaff(CardId.of(id)));
    }
}
