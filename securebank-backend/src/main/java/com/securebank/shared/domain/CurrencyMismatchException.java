package com.securebank.shared.domain;

import java.util.Currency;

public class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("CURRENCY_MISMATCH", "Expected " + expected + " but got " + actual);
    }
}
