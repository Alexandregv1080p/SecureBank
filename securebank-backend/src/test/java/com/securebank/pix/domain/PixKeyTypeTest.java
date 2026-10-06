package com.securebank.pix.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.shared.domain.InvalidValueException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PixKeyTypeTest {

    private static final String CPF = "52998224725";

    @Test
    void cpfKeysAreDigitsOnlyAndNeedValidCheckDigits() {
        assertThat(PixKeyType.CPF.normalize("529.982.247-25")).isEqualTo(CPF);
        assertThat(PixKeyType.CPF.normalize(" 52998224725 ")).isEqualTo(CPF);
        assertThatThrownBy(() -> PixKeyType.CPF.normalize("111.111.111-11")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixKeyType.CPF.normalize("123")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void emailKeysAreLowercasedAndValidated() {
        assertThat(PixKeyType.EMAIL.normalize("  Ana@Example.COM ")).isEqualTo("ana@example.com");
        assertThatThrownBy(() -> PixKeyType.EMAIL.normalize("ana@")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixKeyType.EMAIL.normalize("a".repeat(70) + "@example.com")).isInstanceOf(InvalidValueException.class);
        assertThat(PixKeyType.EMAIL.normalize("a".repeat(65) + "@example.com")).hasSize(77);
    }

    @Test
    void phoneKeysMustBeE164() {
        assertThat(PixKeyType.PHONE.normalize("+5511999998888")).isEqualTo("+5511999998888");
        assertThatThrownBy(() -> PixKeyType.PHONE.normalize("11999998888")).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> PixKeyType.PHONE.normalize("+0123456789012")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void randomKeysAreUuids() {
        assertThat(PixKeyType.RANDOM.normalize("123E4567-E89B-12D3-A456-426614174000"))
                .isEqualTo("123e4567-e89b-12d3-a456-426614174000");
        assertThatThrownBy(() -> PixKeyType.RANDOM.normalize("nao-e-uuid")).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void blankKeysAreRejected() {
        for (PixKeyType type : PixKeyType.values()) {
            assertThatThrownBy(() -> type.normalize(" ")).isInstanceOf(InvalidValueException.class);
            assertThatThrownBy(() -> type.normalize(null)).isInstanceOf(InvalidValueException.class);
        }
    }

    // ---------- o que o usuário digitou -> interpretações possíveis
    @Test
    void anEmailIsOnlyAnEmail() {
        assertThat(PixKeyType.candidates("Ana@Example.com"))
                .containsExactly(new PixKeyType.Candidate(PixKeyType.EMAIL, "ana@example.com"));
        assertThat(PixKeyType.candidates("ana@")).isEmpty();
    }

    @Test
    void aUuidIsARandomKey() {
        assertThat(PixKeyType.candidates("123e4567-e89b-12d3-a456-426614174000"))
                .containsExactly(new PixKeyType.Candidate(PixKeyType.RANDOM, "123e4567-e89b-12d3-a456-426614174000"));
    }

    @Test
    void elevenDigitsCanBeACpfOrAPhoneAndBothAreTried() {
        List<PixKeyType.Candidate> found = PixKeyType.candidates("529.982.247-25");
        assertThat(found).containsExactly(
                new PixKeyType.Candidate(PixKeyType.CPF, "52998224725"),
                new PixKeyType.Candidate(PixKeyType.PHONE, "+5552998224725"));
    }

    @Test
    void elevenDigitsThatAreNotACpfAreOnlyAPhone() {
        assertThat(PixKeyType.candidates("11999998888"))
                .containsExactly(new PixKeyType.Candidate(PixKeyType.PHONE, "+5511999998888"));
        assertThat(PixKeyType.candidates("(11) 99999-8888"))
                .containsExactly(new PixKeyType.Candidate(PixKeyType.PHONE, "+5511999998888"));
    }

    @Test
    void phonesWithCountryCodeAreRecognized() {
        assertThat(PixKeyType.candidates("+55 11 99999-8888"))
                .containsExactly(new PixKeyType.Candidate(PixKeyType.PHONE, "+5511999998888"));
        assertThat(PixKeyType.candidates("5511999998888"))
                .containsExactly(new PixKeyType.Candidate(PixKeyType.PHONE, "+5511999998888"));
        assertThat(PixKeyType.candidates("551133334444"))
                .containsExactly(new PixKeyType.Candidate(PixKeyType.PHONE, "+551133334444"));
    }

    @Test
    void garbageHasNoInterpretation() {
        assertThat(PixKeyType.candidates("")).isEmpty();
        assertThat(PixKeyType.candidates(null)).isEmpty();
        assertThat(PixKeyType.candidates("abc")).isEmpty();
        assertThat(PixKeyType.candidates("123")).isEmpty();
    }
}
