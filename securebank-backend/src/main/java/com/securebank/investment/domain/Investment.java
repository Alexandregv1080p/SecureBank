package com.securebank.investment.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.DomainException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.InvestmentId;
import com.securebank.shared.domain.Money;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

/**
 * Aplicação em renda fixa. O dinheiro SAI da conta (INVEST_OUT) e volta no resgate (INVEST_IN) já com o rendimento
 * líquido de IR. Rende por dia COMPLETO decorrido, com juros compostos: {@code bruto = aplicado × (1+taxa)^(dias/365)}.
 * Em produto com prazo o rendimento para no vencimento. IR regressivo sobre o rendimento: até 180 dias 22,5%, até 360 20%,
 * até 720 17,5%, acima 15%. (Sem IOF; tudo simulado.)
 */
public final class Investment {

    private static final MathContext MC = new MathContext(34);

    private final InvestmentId id;
    private final CustomerId customerId;
    private final AccountId accountId;
    private final String productCode;
    private final String productName;
    private Money principal; // diminui no resgate parcial
    private final BigDecimal annualRate;
    private final Integer termDays; // nulo = liquidez diária
    private final Instant appliedAt;
    private final Instant maturesAt;
    private InvestmentStatus status;
    private Instant redeemedAt;
    private Money redeemedGross;
    private Money redeemedTax;

    /** O que o investimento vale numa data: [net] é o que cairia na conta se resgatado ali. */
    public record Valuation(int days, Money gross, Money yield, Money tax, BigDecimal taxRate, Money net) {}

    private Investment(InvestmentId id, CustomerId customerId, AccountId accountId, String productCode,
            String productName, Money principal, BigDecimal annualRate, Integer termDays, Instant appliedAt,
            Instant maturesAt, InvestmentStatus status, Instant redeemedAt, Money redeemedGross, Money redeemedTax) {
        this.id = id;
        this.customerId = customerId;
        this.accountId = accountId;
        this.productCode = productCode;
        this.productName = productName;
        this.principal = principal;
        this.annualRate = annualRate;
        this.termDays = termDays;
        this.appliedAt = appliedAt;
        this.maturesAt = maturesAt;
        this.status = status;
        this.redeemedAt = redeemedAt;
        this.redeemedGross = redeemedGross;
        this.redeemedTax = redeemedTax;
    }

    public static Investment apply(CustomerId customerId, AccountId accountId, InvestmentProduct product,
            Money principal, Instant now) {
        if (customerId == null || accountId == null || product == null || principal == null || now == null) {
            throw new InvalidValueException("Investment requires customer, account, product, amount and time");
        }
        if (!product.active()) {
            throw new InvalidValueException("This product is not available");
        }
        if (!principal.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
        if (principal.amount().compareTo(product.minAmount()) < 0) {
            throw new InvestmentBelowMinimumException(product.minAmount().toPlainString());
        }
        Instant maturity = product.kind() == InvestmentKind.TERM ? now.plus(Duration.ofDays(product.termDays())) : null;
        return new Investment(InvestmentId.newId(), customerId, accountId, product.code(), product.name(), principal,
                product.annualRate(), product.kind() == InvestmentKind.TERM ? product.termDays() : null, now, maturity,
                InvestmentStatus.ACTIVE, null, null, null);
    }

    public static Investment restore(InvestmentId id, CustomerId customerId, AccountId accountId, String productCode,
            String productName, Money principal, BigDecimal annualRate, Integer termDays, Instant appliedAt,
            Instant maturesAt, InvestmentStatus status, Instant redeemedAt, Money redeemedGross, Money redeemedTax) {
        return new Investment(id, customerId, accountId, productCode, productName, principal, annualRate, termDays,
                appliedAt, maturesAt, status, redeemedAt, redeemedGross, redeemedTax);
    }

    /** Valor em {@code at}. Depois do resgate, é sempre o valor efetivamente pago. */
    public Valuation valuation(Instant at) {
        if (status == InvestmentStatus.REDEEMED) {
            return build(heldDays(redeemedAt), redeemedGross, redeemedTax);
        }
        int days = heldDays(at);
        BigDecimal daily = BigDecimal.valueOf(Math.pow(BigDecimal.ONE.add(annualRate).doubleValue(), 1.0 / 365));
        BigDecimal grossAmount = principal.amount().multiply(daily.pow(days, MC), MC).setScale(2, RoundingMode.HALF_EVEN);
        Money gross = new Money(grossAmount, principal.currency());
        BigDecimal yield = grossAmount.subtract(principal.amount());
        Money tax = new Money(yield.multiply(taxRate(days)).setScale(2, RoundingMode.HALF_UP), principal.currency());
        return build(days, gross, tax);
    }

    private Valuation build(int days, Money gross, Money tax) {
        return new Valuation(days, gross, gross.minus(principal), tax, taxRate(days), gross.minus(tax));
    }

    /**
     * Resgata o líquido desejado ([wanted]); nulo, ou igual/maior que o líquido de hoje, resgata tudo. Parcial: o cliente
     * recebe exatamente [wanted] e o principal diminui na mesma proporção (a data da aplicação não muda, então o que
     * sobra continua rendendo como antes). @return o líquido a creditar na conta.
     */
    public Money redeem(Instant now, Money wanted) {
        if (wanted == null) {
            return redeem(now);
        }
        if (status != InvestmentStatus.ACTIVE) {
            throw new InvestmentAlreadyRedeemedException();
        }
        if (!isMatured(now)) {
            throw new InvestmentNotMaturedException();
        }
        if (!wanted.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
        Valuation v = valuation(now);
        v.net().requireSameCurrency(wanted);
        if (wanted.compareTo(v.net()) >= 0) {
            return redeem(now);
        }
        BigDecimal fraction = wanted.amount().divide(v.net().amount(), 12, RoundingMode.HALF_EVEN);
        BigDecimal cut = principal.amount().multiply(fraction).setScale(2, RoundingMode.HALF_UP);
        if (cut.signum() == 0) {
            throw new InvalidValueException("Amount is too small to redeem");
        }
        if (cut.compareTo(principal.amount()) >= 0) {
            return redeem(now);
        }
        // ponytail: arredondamento ao centavo no principal pode deslocar o que sobra em até R$ 0,01; sem corrigir
        principal = principal.minus(new Money(cut, principal.currency()));
        return wanted;
    }

    /** Resgata tudo. Produto com prazo só no vencimento. @return o líquido a creditar na conta. */
    public Money redeem(Instant now) {
        if (status != InvestmentStatus.ACTIVE) {
            throw new InvestmentAlreadyRedeemedException();
        }
        if (!isMatured(now)) {
            throw new InvestmentNotMaturedException();
        }
        Valuation v = valuation(now);
        status = InvestmentStatus.REDEEMED;
        redeemedAt = now;
        redeemedGross = v.gross();
        redeemedTax = v.tax();
        return v.net();
    }

    public boolean isMatured(Instant now) {
        return maturesAt == null || !now.isBefore(maturesAt);
    }

    public boolean canRedeem(Instant now) {
        return status == InvestmentStatus.ACTIVE && isMatured(now);
    }

    /** Dias completos desde a aplicação; em produto com prazo, o rendimento (e a contagem do IR) para no vencimento. */
    private int heldDays(Instant at) {
        int days = daysBetween(appliedAt, at);
        return termDays == null ? days : Math.min(days, termDays);
    }

    static int daysBetween(Instant from, Instant to) {
        return (int) Math.max(0, Duration.between(from, to).toDays());
    }

    /** Tabela regressiva do IR sobre o rendimento. */
    public static BigDecimal taxRate(int days) {
        if (days <= 180) {
            return new BigDecimal("0.225");
        }
        if (days <= 360) {
            return new BigDecimal("0.20");
        }
        return days <= 720 ? new BigDecimal("0.175") : new BigDecimal("0.15");
    }

    public boolean isOwnedBy(CustomerId requester) {
        return customerId.equals(requester);
    }

    public InvestmentId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public AccountId accountId() { return accountId; }
    public String productCode() { return productCode; }
    public String productName() { return productName; }
    public Money principal() { return principal; }
    public BigDecimal annualRate() { return annualRate; }
    public Integer termDays() { return termDays; }
    public Instant appliedAt() { return appliedAt; }
    public Instant maturesAt() { return maturesAt; }
    public InvestmentStatus status() { return status; }
    public Instant redeemedAt() { return redeemedAt; }
    public Money redeemedGross() { return redeemedGross; }
    public Money redeemedTax() { return redeemedTax; }
}
