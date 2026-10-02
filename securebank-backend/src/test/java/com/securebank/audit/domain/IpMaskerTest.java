package com.securebank.audit.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IpMaskerTest {

    @Test
    void ipv4LosesTheLastOctet() {
        assertThat(IpMasker.mask("203.0.113.57")).isEqualTo("203.0.113.0");
        assertThat(IpMasker.mask("127.0.0.1")).isEqualTo("127.0.0.0");
    }

    @Test
    void ipv6KeepsOnlyThePrefix() {
        assertThat(IpMasker.mask("2001:db8:85a3:8d3:1319:8a2e:370:7348")).isEqualTo("2001:db8:85a3::");
        assertThat(IpMasker.mask("2001:db8::1")).isEqualTo("2001:db8:0::"); // notação comprimida
        assertThat(IpMasker.mask("::1")).isEqualTo("0:0:0::"); // não pode vazar o endereço inteiro
        assertThat(IpMasker.mask("::ffff:203.0.113.57")).isEqualTo("203.0.113.0"); // IPv4 mapeado
    }

    @Test
    void missingOrUnknownValuesDoNotLeakAnything() {
        assertThat(IpMasker.mask(null)).isNull();
        assertThat(IpMasker.mask("  ")).isNull();
        assertThat(IpMasker.mask("localhost")).isEqualTo("masked");
    }
}
