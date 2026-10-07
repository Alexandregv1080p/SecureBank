package com.securebank.fx.domain;

import com.securebank.shared.domain.DomainException;

public class InsufficientFxFundsException extends DomainException {

    public InsufficientFxFundsException() {
        super("INSUFFICIENT_FX_FUNDS", "Insufficient balance in the foreign currency wallet");
    }
}
