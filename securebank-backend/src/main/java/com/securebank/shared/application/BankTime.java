package com.securebank.shared.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** Relógio do banco: instantes em UTC, mas "dia" (limite diário, extrato) é o de America/Sao_Paulo. */
@Component
public class BankTime {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    private final Clock clock;

    public BankTime(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public Instant startOfToday() {
        return startOfDay(LocalDate.now(clock.withZone(ZONE)));
    }

    public Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }
}
