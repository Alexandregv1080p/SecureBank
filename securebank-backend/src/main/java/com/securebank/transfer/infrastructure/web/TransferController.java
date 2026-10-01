package com.securebank.transfer.infrastructure.web;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.TransferId;
import com.securebank.shared.infrastructure.web.CurrentCustomer;
import com.securebank.shared.infrastructure.web.MoneyResponse;
import com.securebank.transfer.application.TransferApplicationService;
import com.securebank.transfer.domain.Transfer;
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
@RequestMapping("/api/v1/transfers")
class TransferController {

    record TransferRequest(
            @NotNull UUID sourceAccountId,
            @NotBlank @Size(max = 4) String destinationBranch,
            @NotBlank @Size(max = 14) String destinationAccountNumber,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @Size(max = 140) String description) {}

    record TransferResponse(UUID id, UUID sourceAccountId, UUID destinationAccountId, MoneyResponse amount,
            String description, String status, UUID debitTransactionId, UUID creditTransactionId, Instant createdAt) {

        static TransferResponse of(Transfer t) {
            return new TransferResponse(t.id().value(), t.sourceAccountId().value(), t.destinationAccountId().value(),
                    MoneyResponse.of(t.amount()), t.description(), t.status().name(),
                    t.debitTransactionId() == null ? null : t.debitTransactionId().value(),
                    t.creditTransactionId() == null ? null : t.creditTransactionId().value(), t.createdAt());
        }
    }

    record TransferPage(List<TransferResponse> items, int page, int size, long totalElements) {}

    private final TransferApplicationService transfers;
    private final CurrentCustomer current;

    TransferController(TransferApplicationService transfers, CurrentCustomer current) {
        this.transfers = transfers;
        this.current = current;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TransferResponse create(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return TransferResponse.of(transfers.transfer(current.id(), new AccountId(request.sourceAccountId()),
                request.destinationBranch(), request.destinationAccountNumber(), request.amount(),
                request.description(), idempotencyKey));
    }

    @GetMapping
    TransferPage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = transfers.list(current.id(), page, size);
        return new TransferPage(result.items().stream().map(TransferResponse::of).toList(), result.page(),
                result.size(), result.totalElements());
    }

    @GetMapping("/{id}")
    TransferResponse get(@PathVariable String id) {
        return TransferResponse.of(transfers.get(current.id(), TransferId.of(id)));
    }
}
