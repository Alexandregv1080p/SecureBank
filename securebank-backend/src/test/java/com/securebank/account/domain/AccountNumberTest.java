package com.securebank.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.shared.domain.InvalidValueException;
import org.junit.jupiter.api.Test;

class AccountNumberTest {

    @Test
    void generatesSixDigitsPlusCheckDigit() {
        // 100000: soma = 1*7 = 7 → 11 - 7 = 4;  100001: 1*2 + 1*7 = 9 → 11 - 9 = 2
        assertThat(AccountNumber.generate(100000).value()).isEqualTo("100000-4");
        assertThat(AccountNumber.generate(100001).value()).isEqualTo("100001-2");
    }

    @Test
    void checkDigitIsZeroWhenRemainderIsLessThanTwo() {
        assertThat(AccountNumber.generate(11).value()).isEqualTo("000011-6"); // 1*2 + 1*3 = 5 → 11 - 5
        assertThat(AccountNumber.generate(0).value()).isEqualTo("000000-0"); // resto 0 → DV 0
    }

    @Test
    void generatedNumbersAlwaysMatchTheFormat() {
        for (long seq : new long[] {0, 1, 99_999, 100_000, 123_456_789, 999_999_999_99L}) {
            assertThat(AccountNumber.generate(seq).value()).matches("\\d{6,12}-\\d");
        }
        assertThatThrownBy(() -> AccountNumber.generate(-1)).isInstanceOf(InvalidValueException.class);
    }
}
