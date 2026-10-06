package com.securebank.pix.domain;

/** EXPIRED nunca é gravado: é derivado de ACTIVE quando o prazo passa (ver {@link PixCharge#statusAt}). */
public enum PixChargeStatus {
    ACTIVE, PAID, CANCELED, EXPIRED
}
