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

    @Test
    void theInvoiceStatusFollowsTheCalendarAndWhatWasPaid() {
        CreditTerms t = terms(10);
        YearMonth month = YearMonth.of(2026, 11); // fecha dia 3, vence dia 10
        Money total = Money.brl("200.00");
        Money none = Money.zero(Money.BRL);
        assertThat(Invoice.of(t, month, total, none, LocalDate.of(2026, 11, 3)).status()).isEqualTo(InvoiceStatus.OPEN);
        assertThat(Invoice.of(t, month, total, none, LocalDate.of(2026, 11, 4)).status()).isEqualTo(InvoiceStatus.CLOSED);
        assertThat(Invoice.of(t, month, total, none, LocalDate.of(2026, 11, 10)).status()).isEqualTo(InvoiceStatus.CLOSED); // vence no dia: ainda dá tempo
        assertThat(Invoice.of(t, month, total, none, LocalDate.of(2026, 11, 11)).status()).isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(Invoice.of(t, month, total, Money.brl("50.00"), LocalDate.of(2026, 11, 11)).status()).isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(Invoice.of(t, month, total, total, LocalDate.of(2026, 11, 11)).status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(Invoice.of(t, month, none, none, LocalDate.of(2026, 11, 11)).status()).isNotEqualTo(InvoiceStatus.OVERDUE); // fatura vazia
    }

    @Test
    void payingMoreThanWhatIsLeftOrNothingIsRefused() {
        CreditTerms t = terms(10);
        Invoice invoice = Invoice.of(t, YearMonth.of(2026, 11), Money.brl("200.00"), Money.brl("50.00"), LocalDate.of(2026, 11, 5));
        assertThat(invoice.remaining()).isEqualTo(Money.brl("150.00"));
        invoice.ensureCanPay(Money.brl("150.00"));
        invoice.ensureCanPay(Money.brl("0.01"));
        assertThatThrownBy(() -> invoice.ensureCanPay(Money.brl("150.01"))).isInstanceOf(InvoiceNotPayableException.class);
        assertThatThrownBy(() -> invoice.ensureCanPay(Money.zero(Money.BRL))).isInstanceOf(InvalidValueException.class);
        Invoice paid = Invoice.of(t, YearMonth.of(2026, 11), Money.brl("200.00"), Money.brl("200.00"), LocalDate.of(2026, 11, 5));
        assertThatThrownBy(() -> paid.ensureCanPay(Money.brl("1.00"))).isInstanceOf(InvoiceNotPayableException.class);
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
