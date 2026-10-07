package com.securebank.fx.infrastructure.web;

import com.securebank.fx.application.FxApplicationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operação do câmbio pela equipe (MANAGE_FX_RATES, só ADMIN): ajusta a cotação comercial e o spread de uma moeda. */
@RestController
@RequestMapping("/api/v1/admin/fx")
class FxAdminController {

    /** [spreadPercent] em porcentagem (1.5 = 1,5% para cada lado). */
    record RateRequest(
            @NotNull @DecimalMin("0.000001") @Digits(integer = 6, fraction = 6) BigDecimal mid,
            @NotNull @DecimalMin("0") @Digits(integer = 3, fraction = 2) BigDecimal spreadPercent) {}

    private final FxApplicationService fx;

    FxAdminController(FxApplicationService fx) {
        this.fx = fx;
    }

    /** Cotações vigentes para a tela de operação (o cliente lê as dele em /fx/rates). */
    @GetMapping("/rates")
    List<FxController.RateResponse> rates() {
        return fx.rates().stream().map(FxController.RateResponse::of).toList();
    }

    @PutMapping("/rates/{currency}")
    FxController.RateResponse changeRate(@PathVariable String currency, @Valid @RequestBody RateRequest request) {
        return FxController.RateResponse.of(fx.changeRate(currency, request.mid(),
                request.spreadPercent().movePointLeft(2)));
    }
}
