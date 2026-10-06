package com.securebank.account.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountControllerCsvTest {

    @Test
    void nullBecomesEmptyAndTextIsQuoted() {
        assertThat(AccountController.csvText(null)).isEmpty();
        assertThat(AccountController.csvText("")).isEqualTo("\"\"");
        assertThat(AccountController.csvText("pagamento, mercado")).isEqualTo("\"pagamento, mercado\"");
    }

    @Test
    void quotesAreDoubled() {
        assertThat(AccountController.csvText("ele disse \"oi\"")).isEqualTo("\"ele disse \"\"oi\"\"\"");
    }

    @Test
    void formulaPrefixesAreNeutralizedSoSpreadsheetsDoNotExecuteThem() {
        for (String evil : new String[] {"=1+1", "+1", "-1", "@SUM(A1)", "\tx", "\rx"}) {
            assertThat(AccountController.csvText(evil)).startsWith("\"'");
        }
        assertThat(AccountController.csvText("normal")).isEqualTo("\"normal\"");
    }
}
