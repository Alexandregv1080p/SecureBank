package com.securebank.fx.infrastructure.web;

import com.securebank.fx.application.FxApplicationService;
import com.securebank.fx.domain.FxOperation;
import com.securebank.fx.domain.FxRate;
import com.securebank.shared.domain.AccountId;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/fx")
class FxController {

    /** [quotedRate] é a cotação que o cliente viu (compra: buyRate; venda: sellRate); mudou no servidor = recusa. */
    record TradeRequest(
            @NotNull UUID accountId,
            @NotBlank @Size(min = 3, max = 3) String currency,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
            @NotNull @DecimalMin("0.000001") @Digits(integer = 6, fraction = 6) BigDecimal quotedRate) {}

    record RateResponse(String currency, String mid, String buyRate, String sellRate, String spreadPercent,
            Instant updatedAt) {

        static RateResponse of(FxRate r) {
            return new RateResponse(r.currency().getCurrencyCode(), r.mid().toPlainString(), r.ask().toPlainString(),
                    r.bid().toPlainString(), r.spread().movePointRight(2).stripTrailingZeros().toPlainString(),
                    r.updatedAt());
        }
    }

    record WalletResponse(String currency, MoneyResponse balance) {}

    record OperationResponse(UUID id, UUID accountId, String side, MoneyResponse foreignAmount, String rate,
            MoneyResponse brlAmount, Instant createdAt) {

        static OperationResponse of(FxOperation o) {
            return new OperationResponse(o.id(), o.accountId().value(), o.side().name(),
                    MoneyResponse.of(o.foreignAmount()), o.rate().toPlainString(), MoneyResponse.of(o.brlAmount()),
                    o.createdAt());
        }
    }

    record HistoryResponse(List<OperationResponse> items, int page, int size, long totalElements) {}

    private final FxApplicationService fx;
    private final CurrentUser current;

    FxController(FxApplicationService fx, CurrentUser current) {
        this.fx = fx;
        this.current = current;
    }

    @GetMapping("/rates")
    List<RateResponse> rates() {
        return fx.rates().stream().map(RateResponse::of).toList();
    }

    @GetMapping("/wallets")
    List<WalletResponse> wallets() {
        return fx.wallets(current.customerId()).stream()
                .map(w -> new WalletResponse(w.currency().getCurrencyCode(), MoneyResponse.of(w.balance()))).toList();
    }

    @PostMapping("/buy")
    @ResponseStatus(HttpStatus.CREATED)
    OperationResponse buy(@Valid @RequestBody TradeRequest r) {
        return OperationResponse.of(fx.buy(current.customerId(), new AccountId(r.accountId()), r.currency(), r.amount(),
                r.quotedRate()));
    }

    @PostMapping("/sell")
    @ResponseStatus(HttpStatus.CREATED)
    OperationResponse sell(@Valid @RequestBody TradeRequest r) {
        return OperationResponse.of(fx.sell(current.customerId(), new AccountId(r.accountId()), r.currency(),
                r.amount(), r.quotedRate()));
    }

    @GetMapping("/operations")
    HistoryResponse history(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = fx.history(current.customerId(), page, size);
        return new HistoryResponse(result.items().stream().map(OperationResponse::of).toList(), result.page(),
                result.size(), result.totalElements());
    }
}
