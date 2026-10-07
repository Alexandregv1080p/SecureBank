package com.securebank.investment.infrastructure.web;

import com.securebank.investment.application.InvestmentApplicationService;
import com.securebank.investment.application.InvestmentApplicationService.View;
import com.securebank.investment.domain.InvestmentProduct;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.InvestmentId;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/investments")
class InvestmentController {

    record ApplyRequest(
            @NotNull UUID accountId,
            @NotBlank @Size(max = 30) String productCode,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount) {}

    /** [annualRatePercent] já em porcentagem ("10.50"); [termDays] nulo = liquidez diária. */
    record ProductResponse(String code, String name, String kind, String annualRatePercent, Integer termDays,
            String minAmount) {

        static ProductResponse of(InvestmentProduct p) {
            return new ProductResponse(p.code(), p.name(), p.kind().name(), percent(p.annualRate()), p.termDays(),
                    p.minAmount().setScale(2).toPlainString());
        }
    }

    /** Os valores (bruto, rendimento, IR, líquido) são "se resgatar agora"; depois do resgate, o que foi pago. */
    record InvestmentResponse(UUID id, UUID accountId, String productCode, String productName, MoneyResponse principal,
            String annualRatePercent, Integer termDays, Instant appliedAt, Instant maturesAt, String status,
            int daysHeld, MoneyResponse gross, MoneyResponse yield, MoneyResponse tax, String taxRatePercent,
            MoneyResponse net, boolean canRedeem, Instant redeemedAt) {

        static InvestmentResponse of(View v) {
            var i = v.investment();
            var val = v.valuation();
            return new InvestmentResponse(i.id().value(), i.accountId().value(), i.productCode(), i.productName(),
                    MoneyResponse.of(i.principal()), percent(i.annualRate()), i.termDays(), i.appliedAt(),
                    i.maturesAt(), i.status().name(), val.days(), MoneyResponse.of(val.gross()),
                    MoneyResponse.of(val.yield()), MoneyResponse.of(val.tax()), percent(val.taxRate()),
                    MoneyResponse.of(val.net()), v.canRedeem(), i.redeemedAt());
        }
    }

    static String percent(BigDecimal rate) {
        return rate.movePointRight(2).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private final InvestmentApplicationService investments;
    private final CurrentUser current;

    InvestmentController(InvestmentApplicationService investments, CurrentUser current) {
        this.investments = investments;
        this.current = current;
    }

    @GetMapping("/products")
    List<ProductResponse> products() {
        return investments.products().stream().map(ProductResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    InvestmentResponse apply(@Valid @RequestBody ApplyRequest request) {
        return InvestmentResponse.of(investments.apply(current.customerId(), new AccountId(request.accountId()),
                request.productCode(), request.amount()));
    }

    @GetMapping
    List<InvestmentResponse> list() {
        return investments.list(current.customerId()).stream().map(InvestmentResponse::of).toList();
    }

    @GetMapping("/{id}")
    InvestmentResponse get(@PathVariable String id) {
        return InvestmentResponse.of(investments.get(current.customerId(), InvestmentId.of(id)));
    }

    @PostMapping("/{id}/redeem")
    @ResponseStatus(HttpStatus.CREATED)
    InvestmentResponse redeem(@PathVariable String id) {
        return InvestmentResponse.of(investments.redeem(current.customerId(), InvestmentId.of(id)));
    }
}
