package com.securebank.pix.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/** Identificador único de uma transação Pix, no formato do Banco Central: E + ISPB (8) + data/hora UTC (12) + 11 alfanuméricos. */
public final class EndToEndId {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);
    /** ISPB fictício deste banco simulado. */
    static final String ISPB = "00000000";

    private EndToEndId() {}

    public static String generate(Instant now, Random random) {
        StringBuilder id = new StringBuilder("E").append(ISPB).append(STAMP.format(now));
        for (int i = 0; i < 11; i++) {
            id.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return id.toString();
    }
}
