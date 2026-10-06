package com.securebank.pix.infrastructure.web;

import com.securebank.pix.application.PixApplicationService;
import com.securebank.pix.domain.PixKey;
import com.securebank.pix.domain.PixKeyType;
import com.securebank.pix.domain.PixTransfer;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.PixKeyId;
import com.securebank.shared.domain.PixTransferId;
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
@RequestMapping("/api/v1/pix")
class PixController {

    /** O valor da chave NUNCA vem do cliente: CPF, e-mail e celular saem do cadastro; a aleatória é gerada aqui. */
    record RegisterKeyRequest(@NotNull UUID accountId, @NotNull PixKeyType type) {}

    record KeyResponse(UUID id, UUID accountId, String type, String key, Instant createdAt) {
        static KeyResponse of(PixKey k) {
            return new KeyResponse(k.id().value(), k.accountId().value(), k.type().name(), k.value(), k.createdAt());
        }
    }

    record LookupResponse(String type, String key, String name, String document, String bank, boolean ownAccount) {
        static LookupResponse of(PixApplicationService.Lookup l) {
            return new LookupResponse(l.type().name(), l.key(), l.maskedName(), l.maskedDocument(), "SecureBank",
                    l.ownAccount());
        }
    }

    record SendRequest(
            @NotNull UUID sourceAccountId,
            @NotBlank @Size(max = 100) String key,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @Size(max = PixTransfer.MESSAGE_MAX) String message) {}

    /** Opcional: sem valor, devolve tudo o que ainda resta. */
    record RefundRequest(@DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount) {}

    record PixResponse(UUID id, String endToEndId, UUID sourceAccountId, MoneyResponse amount, String message,
            String key, String counterpartName, String direction, UUID refundOfId, MoneyResponse refundedAmount,
            MoneyResponse refundableAmount, Instant createdAt) {

        static PixResponse sent(PixTransfer p) {
            return new PixResponse(p.id().value(), p.endToEndId(), p.sourceAccountId().value(),
                    MoneyResponse.of(p.amount()), p.message(), p.destinationKey(), p.destinationName(), "SENT",
                    p.refundOfId() == null ? null : p.refundOfId().value(), null, null, p.createdAt());
        }

        static PixResponse of(PixApplicationService.Entry e) {
            PixTransfer p = e.pix();
            return new PixResponse(p.id().value(), p.endToEndId(), p.sourceAccountId().value(),
                    MoneyResponse.of(p.amount()), p.message(), p.destinationKey(), e.counterpartName(),
                    e.sent() ? "SENT" : "RECEIVED", p.refundOfId() == null ? null : p.refundOfId().value(),
                    MoneyResponse.of(e.refunded()), MoneyResponse.of(e.refundable()), p.createdAt());
        }
    }

    record PixPage(List<PixResponse> items, int page, int size, long totalElements) {}

    private final PixApplicationService pix;
    private final CurrentUser current;

    PixController(PixApplicationService pix, CurrentUser current) {
        this.pix = pix;
        this.current = current;
    }

    @PostMapping("/keys")
    @ResponseStatus(HttpStatus.CREATED)
    KeyResponse registerKey(@Valid @RequestBody RegisterKeyRequest request) {
        return KeyResponse.of(pix.registerKey(current.customerId(), new AccountId(request.accountId()), request.type()));
    }

    @GetMapping("/keys")
    List<KeyResponse> keys() {
        return pix.listKeys(current.customerId()).stream().map(KeyResponse::of).toList();
    }

    @DeleteMapping("/keys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteKey(@PathVariable String id) {
        pix.deleteKey(current.customerId(), PixKeyId.of(id));
    }

    @GetMapping("/keys/lookup")
    LookupResponse lookup(@RequestParam("key") @Size(max = 100) String key) {
        return LookupResponse.of(pix.lookup(current.customerId(), key));
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    PixResponse send(@RequestHeader("Idempotency-Key") String idempotencyKey, @Valid @RequestBody SendRequest request) {
        return PixResponse.sent(pix.send(current.customerId(), new AccountId(request.sourceAccountId()), request.key(),
                request.amount(), request.message()));
    }

    @GetMapping("/transfers/{id}")
    PixResponse get(@PathVariable String id) {
        return PixResponse.of(pix.get(current.customerId(), PixTransferId.of(id)));
    }

    /** Devolve (parte de) um Pix recebido, em até 90 dias. A resposta é a devolução (um Pix enviado). */
    @PostMapping("/transfers/{id}/refund")
    @ResponseStatus(HttpStatus.CREATED)
    PixResponse refund(@PathVariable String id, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody(required = false) RefundRequest request) {
        return PixResponse.sent(pix.refund(current.customerId(), PixTransferId.of(id),
                request == null ? null : request.amount()));
    }

    @GetMapping("/transfers")
    PixPage history(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = pix.history(current.customerId(), page, size);
        return new PixPage(result.items().stream().map(PixResponse::of).toList(), result.page(), result.size(),
                result.totalElements());
    }
}
