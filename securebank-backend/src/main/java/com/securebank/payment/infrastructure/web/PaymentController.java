package com.securebank.payment.infrastructure.web;

import com.securebank.payment.application.PaymentApplicationService;
import com.securebank.payment.domain.Payment;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.PaymentId;
import com.securebank.shared.infrastructure.web.CurrentCustomer;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController {

    record PaymentRequest(
            @NotNull UUID accountId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 48) String barcode,
            @Size(max = 140) String description) {}

    record PaymentResponse(UUID id, UUID accountId, MoneyResponse amount, String barcode, String description,
            String status, UUID transactionId, Instant createdAt) {

        static PaymentResponse of(Payment p) {
            return new PaymentResponse(p.id().value(), p.accountId().value(), MoneyResponse.of(p.amount()),
                    p.barcode(), p.description(), p.status().name(),
                    p.transactionId() == null ? null : p.transactionId().value(), p.createdAt());
        }
    }

    record PaymentPage(List<PaymentResponse> items, int page, int size, long totalElements) {}

    private final PaymentApplicationService payments;
    private final CurrentCustomer current;

    PaymentController(PaymentApplicationService payments, CurrentCustomer current) {
        this.payments = payments;
        this.current = current;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PaymentResponse create(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PaymentRequest request) {
        return PaymentResponse.of(payments.pay(current.id(), new AccountId(request.accountId()), request.amount(),
                request.barcode(), request.description(), idempotencyKey));
    }

    @GetMapping
    PaymentPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = payments.list(current.id(), page, size);
        return new PaymentPage(result.items().stream().map(PaymentResponse::of).toList(), result.page(),
                result.size(), result.totalElements());
    }

    @GetMapping("/{id}")
    PaymentResponse get(@PathVariable String id) {
        return PaymentResponse.of(payments.get(current.id(), PaymentId.of(id)));
    }
}
