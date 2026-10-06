package com.securebank.piggy.domain;

import com.securebank.shared.domain.DomainException;

public class PiggyClosedException extends DomainException {

    public PiggyClosedException() {
        super("PIGGY_CLOSED", "This piggy bank is closed");
    }
}
