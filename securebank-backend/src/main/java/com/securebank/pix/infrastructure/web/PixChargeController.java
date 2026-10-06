package com.securebank.pix.infrastructure.web;

import com.securebank.pix.application.PixChargeApplicationService;
import com.securebank.pix.domain.PixCharge;
import com.securebank.pix.domain.PixTransfer;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.infrastructure.web.CurrentUser;
import com.securebank.shared.infrastructure.web.MoneyResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/v1/pix/charges")
class PixChargeController {

    record CreateRequest(
            @NotNull UUID accountId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @Size(max = PixCharge.DESCRIPTION_MAX) String description,
            @Min(1) @Max(10080) Integer expiresInMinutes) {}

    record PayRequest(@NotNull UUID sourceAccountId) {}

    /** Cobrança de quem cobra. {@code location} vai no QR dinâmico (campo 25 do BR Code). */
    record ChargeResponse(String txid, UUID accountId, MoneyResponse amount, String description, String status,
            Instant expiresAt, Instant createdAt, Instant paidAt, String location) {

        static ChargeResponse of(PixCharge c, Instant now, String locationBase) {
            return new ChargeResponse(c.txid(), c.accountId().value(), MoneyResponse.of(c.amount()), c.description(),
                    c.statusAt(now).name(), c.expiresAt(), c.createdAt(), c.paidAt(), locationBase + c.txid());
        }
    }

    /** O que o pagador vê: só o necessário para decidir (nome e CPF mascarados). */
    record ViewResponse(String txid, MoneyResponse amount, String description, String status, Instant expiresAt,
            String receiverName, String receiverDocument, boolean own, String bank) {

        static ViewResponse of(PixChargeApplicationService.View v) {
            PixCharge c = v.charge();
            return new ViewResponse(c.txid(), MoneyResponse.of(c.amount()), c.description(), v.status().name(),
                    c.expiresAt(), v.receiverName(), v.receiverDocument(), v.own(), "SecureBank");
        }
    }

    record ChargePage(List<ChargeResponse> items, int page, int size, long totalElements) {}

    private final PixChargeApplicationService charges;
    private final CurrentUser current;
    private final String locationBase;

    PixChargeController(PixChargeApplicationService charges, CurrentUser current,
            @Value("${securebank.pix.charge-location:pix.securebank.example/charges/}") String locationBase) {
        this.charges = charges;
        this.current = current;
        this.locationBase = locationBase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ChargeResponse create(@Valid @RequestBody CreateRequest request) {
        PixCharge charge = charges.create(current.customerId(), new AccountId(request.accountId()), request.amount(),
                request.description(), request.expiresInMinutes());
        return ChargeResponse.of(charge, Instant.now(), locationBase);
    }

    @GetMapping
    ChargePage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = charges.list(current.customerId(), page, size);
        Instant now = Instant.now();
        return new ChargePage(result.items().stream().map(c -> ChargeResponse.of(c, now, locationBase)).toList(),
                result.page(), result.size(), result.totalElements());
    }

    @GetMapping("/{txid}")
    ViewResponse view(@PathVariable String txid) {
        return ViewResponse.of(charges.view(current.customerId(), txid));
    }

    @PostMapping("/{txid}/pay")
    @ResponseStatus(HttpStatus.CREATED)
    PixController.PixResponse pay(@PathVariable String txid, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PayRequest request) {
        PixTransfer paid = charges.pay(current.customerId(), txid, new AccountId(request.sourceAccountId()));
        return PixController.PixResponse.sent(paid);
    }

    @DeleteMapping("/{txid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable String txid) {
        charges.cancel(current.customerId(), txid);
    }
}
