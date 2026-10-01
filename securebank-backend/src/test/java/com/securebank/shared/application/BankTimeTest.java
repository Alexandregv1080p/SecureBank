package com.securebank.shared.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BankTimeTest {

    @Test
    void todayIsTheSaoPauloDayNotTheUtcDay() {
        // 02:00 UTC de 01/10 ainda é 23:00 de 30/09 em São Paulo (UTC-3): o "dia" do limite diário é o de lá
        BankTime time = new BankTime(Clock.fixed(Instant.parse("2026-10-01T02:00:00Z"), ZoneOffset.UTC));

        assertThat(time.startOfToday()).isEqualTo(Instant.parse("2026-09-30T03:00:00Z"));
    }

    @Test
    void startOfDayUsesTheBankZone() {
        BankTime time = new BankTime(Clock.systemUTC());

        assertThat(time.startOfDay(LocalDate.parse("2026-10-01"))).isEqualTo(Instant.parse("2026-10-01T03:00:00Z"));
    }
}
