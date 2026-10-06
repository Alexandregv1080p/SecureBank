package com.securebank.pix.infrastructure.web;

import com.securebank.pix.application.PixScheduleApplicationService;
import com.securebank.pix.domain.PixSchedule;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.PixScheduleId;
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
import java.time.LocalDate;
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
@RequestMapping("/api/v1/pix/schedules")
class PixScheduleController {

    record CreateRequest(
            @NotNull UUID sourceAccountId,
            @NotBlank @Size(max = 100) String key,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @Size(max = PixSchedule.MESSAGE_MAX) String message,
            @NotNull LocalDate scheduledFor) {}

    record ScheduleResponse(UUID id, UUID sourceAccountId, String key, String destinationName, MoneyResponse amount,
            String message, LocalDate scheduledFor, String status, String failureReason, UUID executedPixId,
            Instant createdAt) {

        static ScheduleResponse of(PixSchedule s) {
            return new ScheduleResponse(s.id().value(), s.sourceAccountId().value(), s.key(), s.destinationName(),
                    MoneyResponse.of(s.amount()), s.message(), s.scheduledFor(), s.status().name(), s.failureReason(),
                    s.executedPixId() == null ? null : s.executedPixId().value(), s.createdAt());
        }
    }

    record SchedulePage(List<ScheduleResponse> items, int page, int size, long totalElements) {}

    private final PixScheduleApplicationService schedules;
    private final CurrentUser current;

    PixScheduleController(PixScheduleApplicationService schedules, CurrentUser current) {
        this.schedules = schedules;
        this.current = current;
    }

    /** Idempotency-Key obrigatória: um retry de rede não cria dois agendamentos. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ScheduleResponse create(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateRequest request) {
        return ScheduleResponse.of(schedules.schedule(current.customerId(), new AccountId(request.sourceAccountId()),
                request.key(), request.amount(), request.message(), request.scheduledFor()));
    }

    @GetMapping
    SchedulePage list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = schedules.list(current.customerId(), page, size);
        return new SchedulePage(result.items().stream().map(ScheduleResponse::of).toList(), result.page(), result.size(),
                result.totalElements());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable String id) {
        schedules.cancel(current.customerId(), PixScheduleId.of(id));
    }
}
