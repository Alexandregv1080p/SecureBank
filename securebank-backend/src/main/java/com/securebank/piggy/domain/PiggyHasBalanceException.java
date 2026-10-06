package com.securebank.piggy.domain;

import com.securebank.shared.domain.DomainException;

public class PiggyHasBalanceException extends DomainException {

    public PiggyHasBalanceException() {
        super("PIGGY_HAS_BALANCE", "A piggy bank with remaining balance cannot be closed");
    }
}
