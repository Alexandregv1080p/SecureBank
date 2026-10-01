package com.securebank.account.domain;

import com.securebank.shared.domain.DomainException;

public class InsufficientFundsException extends DomainException {

    public InsufficientFundsException() {
        super("INSUFFICIENT_FUNDS", "Insufficient funds");
    }
}
