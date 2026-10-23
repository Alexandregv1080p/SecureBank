package com.securebank.card.domain;

import static com.securebank.DomainFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class CardTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

    private static Card card() {
        return Card.issue(CustomerId.newId(), AccountId.newId(), "José da Conceição Álvares", CardNumber.generate(), TODAY, NOW);
    }

    @RepeatedTest(20)
    void generatedNumbersAreLuhnValidFictionalAnd16Digits() {
        String number = CardNumber.generate();
        assertThat(number).hasSize(16).startsWith("9");
        assertThat(CardNumber.isValid(number)).isTrue();
    }

    @Test
    void luhnCatchesATypo() {
        String number = CardNumber.generate();
        char last = number.charAt(15);
        String wrong = number.substring(0, 15) + (char) ('0' + (last - '0' + 1) % 10);
        assertThat(CardNumber.isValid(wrong)).isFalse();
        assertThat(CardNumber.isValid("123")).isFalse();
        assertThat(CardNumber.isValid(null)).isFalse();
        assertThat(CardNumber.isValid("9".repeat(15) + "x")).isFalse();
    }

    @Test
    void theHolderNameIsUppercaseWithoutAccentsAndCapped() {
        assertThat(card().holderName()).isEqualTo("JOSE DA CONCEICAO ALVARES");
        Card longName = Card.issue(CustomerId.newId(), AccountId.newId(), "A".repeat(30) + " " + "B".repeat(30),
                CardNumber.generate(), TODAY, NOW);
        assertThat(longName.holderName().length()).isLessThanOrEqualTo(Card.HOLDER_NAME_MAX);
    }

    @Test
    void aCardValidFiveYearsAndExpiresAtTheEndOfTheMonth() {
        Card card = card();
        assertThat(card.expiry()).isEqualTo(YearMonth.of(2031, 10));
        assertThat(card.isExpired(LocalDate.of(2031, 10, 31))).isFalse();
        assertThat(card.isExpired(LocalDate.of(2031, 11, 1))).isTrue();
        card.ensureCanPurchase(LocalDate.of(2031, 10, 31));
        assertThatThrownBy(() -> card.ensureCanPurchase(LocalDate.of(2031, 11, 1))).isInstanceOf(CardNotUsableException.class);
    }

    @Test
    void onlyAnActiveCardPurchasesAndCancelIsForever() {
        Card card = card();
        card.block("perdi", NOW);
        assertThat(card.status()).isEqualTo(CardStatus.BLOCKED);
        assertThatThrownBy(() -> card.ensureCanPurchase(TODAY)).isInstanceOf(CardNotUsableException.class);
        card.unblock(NOW);
        card.ensureCanPurchase(TODAY);
        card.cancel("nao quero", NOW);
        assertThatThrownBy(() -> card.unblock(NOW)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> card.block("perdi", NOW)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> card.ensureCanPurchase(TODAY)).isInstanceOf(CardNotUsableException.class);
    }

    @Test
    void blockAndCancelKeepTheReasonAndUnblockClearsIt() {
        Card card = card();
        card.block("  Perdi o cartao  ", NOW);
        assertThat(card.statusReason()).isEqualTo("Perdi o cartao");
        card.unblock(NOW);
        assertThat(card.statusReason()).isNull();
        card.cancel("Troquei de banco", NOW);
        assertThat(card.statusReason()).isEqualTo("Troquei de banco");
    }

    @Test
    void theReasonIsRequiredShortAndFreeOfControlCharacters() {
        for (String bad : new String[] {null, "", "   ", "ab", "x".repeat(141), "a" + (char) 10 + "bc"}) {
            assertThatThrownBy(() -> card().block(bad, NOW)).isInstanceOf(InvalidValueException.class);
            assertThatThrownBy(() -> card().cancel(bad, NOW)).isInstanceOf(InvalidValueException.class);
        }
        Card card = card();
        assertThatThrownBy(() -> card.block("ab", NOW)).isInstanceOf(InvalidValueException.class);
        assertThat(card.status()).isEqualTo(CardStatus.ACTIVE); // recusado: nada mudou
    }

    @Test
    void anInvalidNumberOrMissingNameIsRefused() {
        assertThatThrownBy(() -> Card.issue(CustomerId.newId(), AccountId.newId(), "Ana Silva", "123", TODAY, NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> Card.issue(CustomerId.newId(), AccountId.newId(), " ", CardNumber.generate(), TODAY, NOW))
                .isInstanceOf(InvalidValueException.class);
    }
}
