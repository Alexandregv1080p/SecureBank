package com.securebank.authentication.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class TotpTest {

    /** Segredo ASCII "12345678901234567890" do apêndice B do RFC 6238, em Base32. */
    private static final String RFC_SECRET = Base32.encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesTheRfc6238TestVectors() {
        // (tempo em segundos, código de 8 dígitos do RFC) — SHA-1
        long[][] vectors = {{59, 94287082}, {1111111109, 7081804}, {1111111111, 14050471}, {1234567890, 89005924},
                {2000000000, 69279037}, {20000000000L, 65353130}};
        for (long[] v : vectors) {
            String eightDigits = Totp.truncate(RFC_SECRET, v[0] / Totp.STEP_SECONDS, 8);
            assertThat(eightDigits).isEqualTo(String.format("%08d", v[1]));
        }
    }

    @Test
    void sixDigitCodeIsTheLastSixOfTheRfcCode() {
        assertThat(Totp.code(RFC_SECRET, 59 / Totp.STEP_SECONDS)).isEqualTo("287082");
    }

    @Test
    void base32RoundTrips() {
        byte[] data = "SecureBank-TOTP!".getBytes(StandardCharsets.UTF_8);
        assertThat(Base32.decode(Base32.encode(data))).isEqualTo(data);
        assertThat(Totp.generateSecret()).matches("[A-Z2-7]{32}");
    }

    @Test
    void acceptsTheCurrentAndNeighbourSteps() {
        Instant now = Instant.parse("2026-10-01T12:00:10Z");
        long step = Totp.stepOf(now);

        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step), now, 0)).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, 0)).hasValue(step - 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, 0)).hasValue(step + 1);
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now, 0)).isEmpty(); // fora da janela
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, 0)).isEmpty();
    }

    @Test
    void aStepAlreadyUsedIsRejectedEvenIfTheCodeIsCorrect() {
        Instant now = Instant.parse("2026-10-01T12:00:10Z");
        long step = Totp.stepOf(now);
        String code = Totp.code(RFC_SECRET, step);

        OptionalLong first = Totp.verify(RFC_SECRET, code, now, 0);
        assertThat(first).hasValue(step);
        assertThat(Totp.verify(RFC_SECRET, code, now, first.getAsLong())).isEmpty(); // replay
        assertThat(Totp.verify(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, step)).isEmpty(); // passo mais velho
    }

    @Test
    void malformedCodesAreRejected() {
        Instant now = Instant.now();
        for (String bad : new String[] {null, "", "12345", "1234567", "abcdef", "12 456"}) {
            assertThat(Totp.verify(RFC_SECRET, bad, now, 0)).isEmpty();
        }
    }

    @Test
    void otpauthUriCarriesTheSecretAndIssuer() {
        String uri = Totp.otpauthUri("SecureBank", "ana@example.com", "JBSWY3DPEHPK3PXP");

        assertThat(uri).startsWith("otpauth://totp/SecureBank:ana%40example.com?secret=JBSWY3DPEHPK3PXP")
                .contains("issuer=SecureBank").contains("digits=6").contains("period=30");
    }
}
