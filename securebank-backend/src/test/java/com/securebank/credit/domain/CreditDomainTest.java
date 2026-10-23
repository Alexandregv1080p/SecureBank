package com.securebank.credit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.DomainFixtures;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreditDomainTest {

    private static CreditTerms terms(int dueDay) {
        return CreditTerms.grant(CardId.newId(), dueDay);
    }

    @Test
    void theDueDayMustBeOneOfTheOfferedOnesAndTheLimitStartsAt2000() {
        assertThat(terms(10).limit()).isEqualTo(Money.brl("2000.00"));
        for (int bad : new int[] {0, 1, 7, 26, 31}) {
            assertThatThrownBy(() -> terms(bad)).isInstanceOf(InvalidValueException.class);
        }
    }

    @Test
    void theInvoiceClosesSevenDaysBeforeItIsDue() {
        CreditTerms t = terms(10);
        assertThat(t.dueDate(YearMonth.of(2026, 11))).isEqualTo(LocalDate.of(2026, 11, 10));
        assertThat(t.closingDate(YearMonth.of(2026, 11))).isEqualTo(LocalDate.of(2026, 11, 3));
        // vencimento no dia 5: o fechamento cai no mês anterior
        assertThat(terms(5).closingDate(YearMonth.of(2026, 11))).isEqualTo(LocalDate.of(2026, 10, 29));
    }

    @Test
    void aPurchaseFallsOnTheFirstInvoiceThatHasNotClosedYet() {
        CreditTerms t = terms(10); // fecha no dia 3
        assertThat(t.invoiceFor(LocalDate.of(2026, 10, 3))).isEqualTo(YearMonth.of(2026, 10)); // no dia do fechamento ainda entra
        assertThat(t.invoiceFor(LocalDate.of(2026, 10, 4))).isEqualTo(YearMonth.of(2026, 11));
        assertThat(t.invoiceFor(LocalDate.of(2026, 10, 1))).isEqualTo(YearMonth.of(2026, 10));
        assertThat(t.invoiceFor(LocalDate.of(2026, 12, 31))).isEqualTo(YearMonth.of(2027, 1));

        CreditTerms early = terms(5); // fecha no dia 29 do mês anterior (28 em fevereiro de 2027: 5 de mar menos 7 dias)
        assertThat(early.invoiceFor(LocalDate.of(2026, 10, 28))).isEqualTo(YearMonth.of(2026, 11));
        assertThat(early.invoiceFor(LocalDate.of(2026, 10, 29))).isEqualTo(YearMonth.of(2026, 11));
        assertThat(early.invoiceFor(LocalDate.of(2026, 10, 30))).isEqualTo(YearMonth.of(2026, 12));
        assertThat(early.invoiceFor(LocalDate.of(2026, 9, 20))).isEqualTo(YearMonth.of(2026, 10));
    }

    @Test
    void installmentsAreWholeCentsAndTheLastOneTakesTheRemainder() {
        List<Money> parts = CreditPurchase.split(Money.brl("100.00"), 3);
        assertThat(parts).containsExactly(Money.brl("33.33"), Money.brl("33.33"), Money.brl("33.34"));
        assertThat(CreditPurchase.split(Money.brl("100.00"), 1)).containsExactly(Money.brl("100.00"));
        assertThat(CreditPurchase.split(Money.brl("0.10"), 3).stream().reduce(Money.zero(Money.BRL), Money::plus)).isEqualTo(Money.brl("0.10"));
    }

    @Test
    void thePlanPutsEachInstallmentOnTheNextInvoice() {
        CreditPurchase p = CreditPurchase.create(terms(10), Money.brl("300.00"), "Loja", 3, LocalDate.of(2026, 10, 23), DomainFixtures.NOW);
        assertThat(p.plan()).extracting(CreditPurchase.Installment::invoiceMonth)
                .containsExactly(YearMonth.of(2026, 11), YearMonth.of(2026, 12), YearMonth.of(2027, 1));
        assertThat(p.plan()).extracting(i -> i.amount().amount().toPlainString()).containsExactly("100.00", "100.00", "100.00");
        assertThat(p.merchant()).isEqualTo("Loja");
    }

    @Test
    void installmentsHaveALimitAndAMinimumValue() {
        CreditTerms t = terms(10);
        LocalDate today = LocalDate.of(2026, 10, 23);
        assertThatThrownBy(() -> CreditPurchase.create(t, Money.brl("100.00"), "Loja", 13, today, DomainFixtures.NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> CreditPurchase.create(t, Money.brl("100.00"), "Loja", 0, today, DomainFixtures.NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> CreditPurchase.create(t, Money.brl("20.00"), "Loja", 5, today, DomainFixtures.NOW)).isInstanceOf(InvalidValueException.class); // 4,00 por parcela
        CreditPurchase.create(t, Money.brl("25.00"), "Loja", 5, today, DomainFixtures.NOW); // 5,00 por parcela
        CreditPurchase.create(t, Money.brl("3.00"), "Loja", 1, today, DomainFixtures.NOW); // à vista não tem mínimo
        assertThatThrownBy(() -> CreditPurchase.create(t, Money.brl("10.00"), "  ", 1, today, DomainFixtures.NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> CreditPurchase.create(t, Money.zero(Money.BRL), "Loja", 1, today, DomainFixtures.NOW)).isInstanceOf(InvalidValueException.class);
    }

    private static Invoice invoice(CreditTerms t, String principal, String charges, String paid, LocalDate today) {
        return Invoice.of(t, YearMonth.of(2026, 11), Money.brl(principal), Money.brl(charges), Money.brl(paid), today);
    }

    @Test
    void theInvoiceStatusFollowsTheCalendarAndWhatWasPaid() {
        CreditTerms t = terms(10); // novembro: fecha dia 3, vence dia 10
        assertThat(invoice(t, "200.00", "0", "0", LocalDate.of(2026, 11, 3)).status()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(invoice(t, "200.00", "0", "0", LocalDate.of(2026, 11, 4)).status()).isEqualTo(InvoiceStatus.CLOSED);
        assertThat(invoice(t, "200.00", "0", "0", LocalDate.of(2026, 11, 10)).status()).isEqualTo(InvoiceStatus.CLOSED); // vence no dia: ainda dá tempo
        assertThat(invoice(t, "200.00", "0", "0", LocalDate.of(2026, 11, 11)).status()).isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(invoice(t, "200.00", "0", "200.00", LocalDate.of(2026, 11, 11)).status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(invoice(t, "0", "0", "0", LocalDate.of(2026, 11, 11)).status()).isNotEqualTo(InvoiceStatus.OVERDUE); // fatura vazia
    }

    @Test
    void theMinimumPaymentIs15PercentWithAFloorOf20AndNeverMoreThanTheInvoice() {
        assertThat(Invoice.minimumOf(Money.brl("1000.00"))).isEqualTo(Money.brl("150.00"));
        assertThat(Invoice.minimumOf(Money.brl("200.00"))).isEqualTo(Money.brl("30.00"));
        assertThat(Invoice.minimumOf(Money.brl("100.00"))).isEqualTo(Money.brl("20.00")); // 15% seria 15,00: vale o piso
        assertThat(Invoice.minimumOf(Money.brl("12.00"))).isEqualTo(Money.brl("12.00")); // fatura menor que o piso: paga tudo
        assertThat(Invoice.minimumOf(Money.zero(Money.BRL))).isEqualTo(Money.zero(Money.BRL));
        assertThat(Invoice.minimumOf(Money.brl("33.33"))).isEqualTo(Money.brl("20.00"));
        assertThat(Invoice.minimumOf(Money.brl("1000.10"))).isEqualTo(Money.brl("150.02")); // arredonda ao centavo
    }

    @Test
    void payingTheMinimumKeepsTheInvoiceInRevolvingAndNotPayingBlocksPurchases() {
        CreditTerms t = terms(10);
        LocalDate afterDue = LocalDate.of(2026, 11, 11);
        Invoice noMinimum = invoice(t, "200.00", "0", "29.99", afterDue); // mínimo é 30,00
        assertThat(noMinimum.status()).isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(noMinimum.blocksPurchases()).isTrue();
        assertThat(noMinimum.minimumDue()).isEqualTo(Money.brl("0.01"));

        Invoice minimum = invoice(t, "200.00", "0", "30.00", afterDue);
        assertThat(minimum.status()).isEqualTo(InvoiceStatus.REVOLVING);
        assertThat(minimum.blocksPurchases()).isFalse();
        assertThat(minimum.minimumDue()).isEqualTo(Money.zero(Money.BRL));
        assertThat(minimum.remaining()).isEqualTo(Money.brl("170.00"));

        // antes do vencimento, ainda é só fatura fechada
        assertThat(invoice(t, "200.00", "0", "30.00", LocalDate.of(2026, 11, 10)).status()).isEqualTo(InvoiceStatus.CLOSED);
    }

    @Test
    void chargesAddToTheInvoiceAndTheMinimumStaysOnThePrincipal() {
        CreditTerms t = terms(10);
        Invoice invoice = invoice(t, "200.00", "10.50", "30.00", LocalDate.of(2026, 11, 20));
        assertThat(invoice.total()).isEqualTo(Money.brl("210.50"));
        assertThat(invoice.remaining()).isEqualTo(Money.brl("180.50"));
        assertThat(invoice.minimum()).isEqualTo(Money.brl("30.00")); // sobre o principal, não sobre os encargos
        assertThat(invoice(t, "200.00", "10.50", "210.50", LocalDate.of(2026, 11, 20)).status()).isEqualTo(InvoiceStatus.PAID);
    }

    @Test
    void lateFeeIs2PercentAndInterestIs10PercentAMonthPerDay() {
        assertThat(CreditCharges.lateFee(Money.brl("200.00"))).isEqualTo(Money.brl("4.00"));
        assertThat(CreditCharges.lateFee(Money.brl("170.00"))).isEqualTo(Money.brl("3.40"));
        assertThat(CreditCharges.lateFee(Money.brl("33.33"))).isEqualTo(Money.brl("0.67"));
        assertThat(CreditCharges.dailyInterest(Money.brl("300.00"))).isEqualTo(Money.brl("1.00")); // 10% / 30 = 0,3333% ao dia
        assertThat(CreditCharges.dailyInterest(Money.brl("200.00"))).isEqualTo(Money.brl("0.67"));
        assertThat(CreditCharges.dailyInterest(Money.brl("0.40"))).isEqualTo(Money.zero(Money.BRL)); // menos de 1 centavo
    }

    @Test
    void payingMoreThanWhatIsLeftOrNothingIsRefused() {
        CreditTerms t = terms(10);
        Invoice invoice = invoice(t, "200.00", "0", "50.00", LocalDate.of(2026, 11, 5));
        assertThat(invoice.remaining()).isEqualTo(Money.brl("150.00"));
        invoice.ensureCanPay(Money.brl("150.00"));
        invoice.ensureCanPay(Money.brl("0.01"));
        assertThatThrownBy(() -> invoice.ensureCanPay(Money.brl("150.01"))).isInstanceOf(InvoiceNotPayableException.class);
        assertThatThrownBy(() -> invoice.ensureCanPay(Money.zero(Money.BRL))).isInstanceOf(InvalidValueException.class);
        Invoice paid = invoice(t, "200.00", "0", "200.00", LocalDate.of(2026, 11, 5));
        assertThatThrownBy(() -> paid.ensureCanPay(Money.brl("1.00"))).isInstanceOf(InvoiceNotPayableException.class);
    }

    @Test
    void theLimitIsAMultipleOf100AndTheCustomerCanLowerItDownToWhatHeOwes() {
        CreditTerms t = terms(10); // limite inicial 2.000,00
        Money none = Money.zero(Money.BRL);
        CreditLimits.checkSelfService(t, Money.brl("1500.00"), none, false);
        CreditLimits.checkSelfService(t, Money.brl("500.00"), Money.brl("500.00"), false); // exatamente a dívida
        CreditLimits.checkSelfService(t, Money.brl("100.00"), none, false);
        assertThatThrownBy(() -> CreditLimits.checkSelfService(t, Money.brl("400.00"), Money.brl("500.00"), false))
                .isInstanceOf(CreditRefusedException.class).extracting("code").isEqualTo("CREDIT_LIMIT_BELOW_USED");
        assertThatThrownBy(() -> CreditLimits.checkSelfService(t, Money.brl("1550.00"), none, false)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> CreditLimits.checkSelfService(t, Money.brl("50.00"), none, false)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> CreditLimits.checkSelfService(t, Money.zero(Money.BRL), none, false)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void anIncreaseNeedsAnInvoiceInOrderAndStopsAt10000ButALowerLimitIsAlwaysAllowed() {
        CreditTerms t = terms(10);
        Money none = Money.zero(Money.BRL);
        CreditLimits.checkSelfService(t, Money.brl("10000.00"), none, false);
        assertThatThrownBy(() -> CreditLimits.checkSelfService(t, Money.brl("10100.00"), none, false))
                .isInstanceOf(CreditRefusedException.class).extracting("code").isEqualTo("CREDIT_LIMIT_ABOVE_MAX");
        assertThatThrownBy(() -> CreditLimits.checkSelfService(t, Money.brl("3000.00"), none, true))
                .isInstanceOf(CreditRefusedException.class).extracting("code").isEqualTo("CREDIT_OVERDUE");
        CreditLimits.checkSelfService(t, Money.brl("1000.00"), none, true); // diminuir com fatura vencida pode
    }

    @Test
    void theStaffDecidesUpTo100000ButNeverBelowTheDebt() {
        Money none = Money.zero(Money.BRL);
        CreditLimits.checkStaff(Money.brl("100000.00"), none);
        CreditLimits.checkStaff(Money.brl("20000.00"), Money.brl("20000.00"));
        assertThatThrownBy(() -> CreditLimits.checkStaff(Money.brl("100100.00"), none)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> CreditLimits.checkStaff(Money.brl("1000.00"), Money.brl("1000.01"))).isInstanceOf(CreditRefusedException.class);
        assertThatThrownBy(() -> CreditLimits.checkStaff(Money.brl("1050.00"), none)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void refinancingUsesAFixedInstallmentAt5PercentAMonth() {
        Refinance.Plan p = Refinance.plan(Money.brl("1000.00"), 6);
        assertThat(p.installment()).isEqualTo(Money.brl("197.02"));
        assertThat(p.total()).isEqualTo(Money.brl("1182.12"));
        assertThat(p.interest()).isEqualTo(Money.brl("182.12"));
        assertThat(Refinance.plan(Money.brl("409.36"), 4).installment()).isEqualTo(Money.brl("115.44"));
        assertThat(Refinance.plan(Money.brl("300.00"), 12).installment()).isEqualTo(Money.brl("33.85"));
        assertThat(Refinance.plan(Money.brl("100.00"), 2).total()).isEqualTo(Money.brl("107.56"));
    }

    @Test
    void refinancingOffersOnlyInstallmentsAboveTheMinimumAndBetween2And12() {
        assertThat(Refinance.options(Money.brl("50.00")).stream().map(Refinance.Plan::installments)).containsExactly(2, 3, 4, 5); // 6x seria R$ 9,85
        assertThat(Refinance.options(Money.brl("1000.00"))).hasSize(11); // de 2x até 12x
        assertThatThrownBy(() -> Refinance.plan(Money.brl("1000.00"), 1)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Refinance.plan(Money.brl("1000.00"), 13)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Refinance.plan(Money.brl("50.00"), 6)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void onlyAClosedInvoiceWithEnoughBalanceCanBeRefinanced() {
        CreditTerms t = terms(10);
        Refinance.requireRefinanceable(invoice(t, "400.00", "0", "0", LocalDate.of(2026, 11, 4))); // fechada
        Refinance.requireRefinanceable(invoice(t, "400.00", "0", "0", LocalDate.of(2026, 11, 12))); // vencida
        Refinance.requireRefinanceable(invoice(t, "400.00", "0", "100.00", LocalDate.of(2026, 11, 12))); // rotativo
        assertThatThrownBy(() -> Refinance.requireRefinanceable(invoice(t, "400.00", "0", "0", LocalDate.of(2026, 11, 2)))).isInstanceOf(InvoiceNotPayableException.class); // aberta
        assertThatThrownBy(() -> Refinance.requireRefinanceable(invoice(t, "400.00", "0", "400.00", LocalDate.of(2026, 11, 12)))).isInstanceOf(InvoiceNotPayableException.class); // paga
        assertThatThrownBy(() -> Refinance.requireRefinanceable(invoice(t, "49.99", "0", "0", LocalDate.of(2026, 11, 4)))).isInstanceOf(InvoiceNotPayableException.class); // pouco saldo
    }

    @Test
    void theRefinancedInstallmentsStartOnTheFirstInvoiceThatHasNotClosed() {
        CreditTerms t = terms(10);
        Refinance.Plan plan = Refinance.plan(Money.brl("400.00"), 3);
        CreditPurchase p = CreditPurchase.refinance(t, plan, YearMonth.of(2026, 11), LocalDate.of(2026, 11, 12), DomainFixtures.NOW);
        assertThat(p.kind()).isEqualTo(CreditPurchase.Kind.REFINANCE);
        assertThat(p.merchant()).isEqualTo("Parcelamento da fatura 11/2026");
        assertThat(p.plan()).extracting(CreditPurchase.Installment::invoiceMonth)
                .containsExactly(YearMonth.of(2026, 12), YearMonth.of(2027, 1), YearMonth.of(2027, 2)); // dia 12/11: a de novembro já fechou
        assertThat(p.plan().stream().map(i -> i.amount().amount().toPlainString())).containsOnly(plan.installment().amount().toPlainString());
        assertThat(p.total()).isEqualTo(plan.total());
    }

    @Test
    void aPurchaseNeedsLimitAndNoOverdueInvoice() {
        CreditTerms t = terms(10);
        CreditPolicy.ensureCanBuy(t, Money.brl("1500.00"), Money.brl("500.00"), false); // exatamente o que sobra
        assertThatThrownBy(() -> CreditPolicy.ensureCanBuy(t, Money.brl("1500.00"), Money.brl("500.01"), false))
                .isInstanceOf(CreditRefusedException.class).hasMessageContaining("limit");
        assertThatThrownBy(() -> CreditPolicy.ensureCanBuy(t, Money.zero(Money.BRL), Money.brl("1.00"), true))
                .isInstanceOf(CreditRefusedException.class).extracting("code").isEqualTo("CREDIT_OVERDUE");
        assertThat(CreditPolicy.available(t, Money.brl("2500.00"))).isEqualTo(Money.zero(Money.BRL)); // nunca negativo
    }
}
