package com.securebank.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Os mesmos casos existem no web (validation.test.ts) e no Android (ValidationTest.kt). */
class BoletoCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "00193373700000001000500940144816060680935031", // código de barras bancário (44)
            "00190500954014481606906809350314337370000000100", // linha digitável bancária (47)
            "846700000017435900240209024050002435842210108119" // arrecadação/convênio (48)
    })
    void acceptsRealBoletos(String code) {
        assertThat(BoletoCode.isValid(code)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "00193373700000001000500940144816060680935032", // 44 com o último dígito trocado
            "00193373800000001000500940144816060680935031", // 44 com um dígito do valor trocado
            "00190500954014481606906809350314337370000000101", // 47 com o último dígito trocado
            "00190500964014481606906809350314337370000000100", // 47 com o campo 1 alterado
            "846700000017435900240209024050002435842210108118", // 48 com o último dígito trocado
            "34191790010104351004791020150008291070026000", // 44 inventado
            "00000000000000000000000000000000000000000000", "000000000000000000000000000000000000000000000000",
            "11111111111111111111111111111111111111111111", "123"
    })
    void refusesTypos(String code) {
        assertThat(BoletoCode.isValid(code)).isFalse();
    }
}
