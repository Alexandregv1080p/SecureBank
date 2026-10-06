package com.securebank.piggy.domain;

import com.securebank.shared.domain.DomainException;

public class InsufficientPiggyFundsException extends DomainException {

    public InsufficientPiggyFundsException() {
        super("INSUFFICIENT_PIGGY_FUNDS", "Insufficient funds in the piggy bank");
    }
}
