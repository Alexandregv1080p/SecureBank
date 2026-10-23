package com.securebank.credit.infrastructure.web;

import com.securebank.card.domain.Card;
import com.securebank.card.domain.CardNumber;
import com.securebank.credit.application.CreditApplicationService;
import com.securebank.credit.application.CreditRepository;
import com.securebank.credit.domain.CreditPurchase;
import com.securebank.credit.domain.CreditTerms;
import com.securebank.credit.domain.Invoice;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.infrastructure.web.CurrentUser;
import com.securebank.shared.infrastructure.web.MoneyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Cartão de crédito: emitir, ver limite e faturas, comprar (à vista ou parcelado) e pagar fatura. */
@RestController
@RequestMapping("/api/v1/credit/cards")
class CreditController {

    record IssueRequest(@NotNull UUID accountId, @NotNull @Min(1) @Max(28) Integer dueDay) {}

    record PurchaseRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = CardPurchaseLimits.MERCHANT_MAX) String merchant,
            @Min(1) @Max(CreditPurchase.MAX_INSTALLMENTS) Integer installments) {}

    record PayRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount) {}

    record IssuedResponse(UUID id, UUID accountId, String kind, String holderName, String maskedNumber, String last4,
            int expiryMonth, int expiryYear, String status, int dueDay, MoneyResponse creditLimit, Instant createdAt) {

        static IssuedResponse of(Card c, CreditTerms terms) {
            return new IssuedResponse(c.id().value(), c.accountId().value(), c.kind().name(), c.holderName(),
                    CardNumber.masked(c.last4()), c.last4(), c.expiry().getMonthValue(), c.expiry().getYear(),
                    c.status().name(), terms.dueDay(), MoneyResponse.of(terms.limit()), c.createdAt());
        }
    }

    record InvoiceResponse(String month, LocalDate closingDate, LocalDate dueDate, MoneyResponse principal, MoneyResponse charges,
            MoneyResponse total, MoneyResponse paid, MoneyResponse remaining, MoneyResponse minimumDue, String status) {

        static InvoiceResponse of(Invoice i) {
            return new InvoiceResponse(i.month().toString(), i.closingDate(), i.dueDate(), MoneyResponse.of(i.principal()),
                    MoneyResponse.of(i.charges()), MoneyResponse.of(i.total()), MoneyResponse.of(i.paid()),
                    MoneyResponse.of(i.remaining()), MoneyResponse.of(i.minimumDue()), i.status().name());
        }
    }

    record SummaryResponse(UUID cardId, MoneyResponse creditLimit, MoneyResponse used, MoneyResponse available, int dueDay,
            boolean overdue, InvoiceResponse currentInvoice) {}

    record ItemResponse(String merchant, int number, int installments, MoneyResponse amount, LocalDate purchasedOn) {}

    record PaymentResponse(MoneyResponse amount, Instant createdAt) {}

    record ChargeResponse(String kind, MoneyResponse amount) {}

    record InvoiceDetailResponse(InvoiceResponse invoice, List<ItemResponse> items, List<ChargeResponse> charges,
            List<PaymentResponse> payments) {}

    record PurchaseResponse(UUID id, UUID cardId, String merchant, MoneyResponse total, int installments,
            MoneyResponse installmentAmount, Instant createdAt) {

        static PurchaseResponse of(CreditPurchase p) {
            MoneyResponse each = p.plan().isEmpty() ? MoneyResponse.of(CreditPurchase.split(p.total(), p.installments()).get(0))
                    : MoneyResponse.of(p.plan().get(0).amount());
            return new PurchaseResponse(p.id(), p.cardId().value(), p.merchant(), MoneyResponse.of(p.total()), p.installments(),
                    each, p.createdAt());
        }
    }

    record PurchasePage(List<PurchaseResponse> items, int page, int size, long totalElements) {}

    /** Limite do texto do estabelecimento, igual ao do débito. */
    static final class CardPurchaseLimits {
        static final int MERCHANT_MAX = 60;

        private CardPurchaseLimits() {}
    }

    private final CreditApplicationService credit;
    private final CurrentUser current;

    CreditController(CreditApplicationService credit, CurrentUser current) {
        this.credit = credit;
        this.current = current;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    IssuedResponse issue(@Valid @RequestBody IssueRequest request) {
        Card card = credit.issue(current.customerId(), new AccountId(request.accountId()), request.dueDay());
        return IssuedResponse.of(card, credit.summary(current.customerId(), card.id()).terms());
    }

    @GetMapping("/{id}")
    SummaryResponse summary(@PathVariable String id) {
        var s = credit.summary(current.customerId(), CardId.of(id));
        return new SummaryResponse(s.card().id().value(), MoneyResponse.of(s.terms().limit()), MoneyResponse.of(s.used()),
                MoneyResponse.of(s.available()), s.terms().dueDay(), s.overdue(), InvoiceResponse.of(s.current()));
    }

    @GetMapping("/{id}/invoices")
    List<InvoiceResponse> invoices(@PathVariable String id) {
        return credit.invoices(current.customerId(), CardId.of(id)).stream().map(InvoiceResponse::of).toList();
    }

    @GetMapping("/{id}/invoices/{month}")
    InvoiceDetailResponse invoice(@PathVariable String id, @PathVariable String month) {
        var d = credit.invoiceDetail(current.customerId(), CardId.of(id), parse(month));
        return new InvoiceDetailResponse(InvoiceResponse.of(d.invoice()),
                d.items().stream().map(CreditController::item).toList(),
                d.charges().stream().map(c -> new ChargeResponse(c.kind(), MoneyResponse.of(c.amount()))).toList(),
                d.payments().stream().map(p -> new PaymentResponse(MoneyResponse.of(p.amount()), p.createdAt())).toList());
    }

    @PostMapping("/{id}/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    PurchaseResponse purchase(@PathVariable String id, @Valid @RequestBody PurchaseRequest request) {
        int installments = request.installments() == null ? 1 : request.installments();
        return PurchaseResponse.of(credit.purchase(current.customerId(), CardId.of(id), request.amount(), request.merchant(), installments));
    }

    @GetMapping("/{id}/purchases")
    PurchasePage purchases(@PathVariable String id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var result = credit.purchases(current.customerId(), CardId.of(id), page, size);
        return new PurchasePage(result.items().stream().map(PurchaseResponse::of).toList(), result.page(), result.size(),
                result.totalElements());
    }

    @PostMapping("/{id}/invoices/{month}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    InvoiceResponse pay(@PathVariable String id, @PathVariable String month, @Valid @RequestBody PayRequest request) {
        return InvoiceResponse.of(credit.payInvoice(current.customerId(), CardId.of(id), parse(month), request.amount()));
    }

    private static ItemResponse item(CreditRepository.Item i) {
        return new ItemResponse(i.merchant(), i.number(), i.installments(), MoneyResponse.of(i.amount()), i.purchasedOn());
    }

    private static YearMonth parse(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new InvalidValueException("Invalid month, use YYYY-MM");
        }
    }
}
