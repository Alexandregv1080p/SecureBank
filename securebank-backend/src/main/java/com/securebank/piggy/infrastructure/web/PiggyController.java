package com.securebank.piggy.infrastructure.web;

import com.securebank.piggy.application.PiggyApplicationService;
import com.securebank.piggy.domain.Piggy;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.PiggyId;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/piggies")
class PiggyController {

    record CreateRequest(
            @NotNull UUID accountId,
            @NotBlank @Size(max = Piggy.NAME_MAX) String name,
            @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal goal) {}

    /** Campos ausentes não mudam; {@code clearGoal: true} remove a meta. */
    record UpdateRequest(
            @Size(max = Piggy.NAME_MAX) String name,
            @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal goal,
            Boolean clearGoal) {}

    record AmountRequest(@NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount) {}

    record PiggyResponse(UUID id, UUID accountId, String name, MoneyResponse balance, MoneyResponse goal,
            Integer progressPercent, boolean goalReached, String status, Instant createdAt) {

        static PiggyResponse of(Piggy p) {
            return new PiggyResponse(p.id().value(), p.accountId().value(), p.name(), MoneyResponse.of(p.balance()),
                    p.goal() == null ? null : MoneyResponse.of(p.goal()), p.progressPercent(), p.goalReached(),
                    p.status().name(), p.createdAt());
        }
    }

    private final PiggyApplicationService piggies;
    private final CurrentUser current;

    PiggyController(PiggyApplicationService piggies, CurrentUser current) {
        this.piggies = piggies;
        this.current = current;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PiggyResponse create(@Valid @RequestBody CreateRequest request) {
        return PiggyResponse.of(piggies.create(current.customerId(), new AccountId(request.accountId()),
                request.name(), request.goal()));
    }

    @GetMapping
    List<PiggyResponse> list() {
        return piggies.list(current.customerId()).stream().map(PiggyResponse::of).toList();
    }

    @GetMapping("/{id}")
    PiggyResponse get(@PathVariable String id) {
        return PiggyResponse.of(piggies.get(current.customerId(), PiggyId.of(id)));
    }

    @PatchMapping("/{id}")
    PiggyResponse update(@PathVariable String id, @Valid @RequestBody UpdateRequest request) {
        return PiggyResponse.of(piggies.update(current.customerId(), PiggyId.of(id), request.name(), request.goal(),
                Boolean.TRUE.equals(request.clearGoal())));
    }

    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    PiggyResponse deposit(@PathVariable String id, @Valid @RequestBody AmountRequest request) {
        return PiggyResponse.of(piggies.deposit(current.customerId(), PiggyId.of(id), request.amount()));
    }

    @PostMapping("/{id}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    PiggyResponse withdraw(@PathVariable String id, @Valid @RequestBody AmountRequest request) {
        return PiggyResponse.of(piggies.withdraw(current.customerId(), PiggyId.of(id), request.amount()));
    }

    /** Fecha o porquinho; o que houver dentro volta para a conta. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void close(@PathVariable String id) {
        piggies.close(current.customerId(), PiggyId.of(id));
    }
}
