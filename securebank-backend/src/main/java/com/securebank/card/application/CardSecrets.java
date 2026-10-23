package com.securebank.card.application;

import java.time.YearMonth;

/** Guarda e deriva os dados sensíveis do cartão: o número fica cifrado em repouso e o CVV nunca é gravado. */
public interface CardSecrets {

    String encrypt(String number);

    String decrypt(String stored);

    /** Impressão digital do número, para garantir que dois cartões não repitam o mesmo. */
    String fingerprint(String number);

    /** CVV de 3 dígitos derivado, com chave do servidor, do número e da validade: sempre o mesmo para o mesmo cartão. */
    String cvv(String number, YearMonth expiry);
}
