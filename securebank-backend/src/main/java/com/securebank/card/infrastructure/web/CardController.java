package com.securebank.card.infrastructure.web;

import com.securebank.card.application.CardApplicationService;
import com.securebank.card.domain.Card;
import com.securebank.card.domain.CardNumber;
import com.securebank.card.domain.CardPurchase;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.infrastructure.web.CurrentUser;
import com.securebank.shared.infrastructure.web.MoneyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cards")
class CardController {

    record IssueRequest(@NotNull UUID accountId) {}

    /** Bloquear e cancelar pedem o motivo, nas palavras do cliente (fica no cartão e na auditoria). */
    record ReasonRequest(@NotBlank @Size(min = Card.REASON_MIN, max = Card.REASON_MAX) String reason) {}

    record PurchaseRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = CardPurchase.MERCHANT_MAX) String merchant) {}

    record CardResponse(UUID id, UUID accountId, String holderName, String maskedNumber, String last4, int expiryMonth,
            int expiryYear, String status, String statusReason, Instant createdAt) {

        static CardResponse of(Card c) {
            return new CardResponse(c.id().value(), c.accountId().value(), c.holderName(), CardNumber.masked(c.last4()),
                    c.last4(), c.expiry().getMonthValue(), c.expiry().getYear(), c.status().name(), c.statusReason(), c.createdAt());
        }
    }

    record DetailsResponse(String number, int expiryMonth, int expiryYear, String cvv, String holderName) {}

    record PurchaseResponse(UUID id, UUID cardId, MoneyResponse amount, String merchant, UUID transactionId,
            Instant createdAt) {

        static PurchaseResponse of(CardPurchase p) {
            return new PurchaseResponse(p.id(), p.cardId().value(), MoneyResponse.of(p.amount()), p.merchant(),
                    p.transactionId().value(), p.createdAt());
        }
    }

    record PurchasePage(List<PurchaseResponse> items, int page, int size, long totalElements) {}

    private final CardApplicationService cards;
    private final CurrentUser current;

    CardController(CardApplicationService cards, CurrentUser current) {
        this.cards = cards;
        this.current = current;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CardResponse issue(@Valid @RequestBody IssueRequest request) {
        return CardResponse.of(cards.issue(current.customerId(), new AccountId(request.accountId())));
    }

    @GetMapping
    List<CardResponse> list() {
        return cards.list(current.customerId()).stream().map(CardResponse::of).toList();
    }

    @GetMapping("/{id}")
    CardResponse get(@PathVariable String id) {
        return CardResponse.of(cards.get(current.customerId(), CardId.of(id)));
    }

    /** Número completo, validade e CVV. É POST (não GET) para nunca ficar em cache, e a resposta não é guardada. */
    @PostMapping("/{id}/reveal")
    ResponseEntity<DetailsResponse> reveal(@PathVariable String id) {
        CardId cardId = CardId.of(id);
        Card card = cards.get(current.customerId(), cardId);
        var details = cards.details(current.customerId(), cardId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new DetailsResponse(details.number(), details.expiry().getMonthValue(), details.expiry().getYear(),
                        details.cvv(), card.holderName()));
    }

    @PostMapping("/{id}/block")
    CardResponse block(@PathVariable String id, @Valid @RequestBody ReasonRequest request) {
        return CardResponse.of(cards.block(current.customerId(), CardId.of(id), request.reason()));
    }

    @PostMapping("/{id}/unblock")
    CardResponse unblock(@PathVariable String id) {
        return CardResponse.of(cards.unblock(current.customerId(), CardId.of(id)));
    }

    /** Definitivo: o cartão cancelado não volta. */
    @PostMapping("/{id}/cancel")
    CardResponse cancel(@PathVariable String id, @Valid @RequestBody ReasonRequest request) {
        return CardResponse.of(cards.cancel(current.customerId(), CardId.of(id), request.reason()));
    }

    /** Compra simulada no débito: sai da conta, consome o limite do cartão e entra no extrato. */
    @PostMapping("/{id}/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    PurchaseResponse purchase(@PathVariable String id, @Valid @RequestBody PurchaseRequest request) {
        return PurchaseResponse.of(cards.purchase(current.customerId(), CardId.of(id), request.amount(), request.merchant()));
    }

    @GetMapping("/{id}/purchases")
    PurchasePage purchases(@PathVariable String id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = cards.purchases(current.customerId(), CardId.of(id), page, size);
        return new PurchasePage(result.items().stream().map(PurchaseResponse::of).toList(), result.page(), result.size(),
                result.totalElements());
    }
}
