package com.securebank.account.domain;

import com.securebank.shared.domain.DomainException;

public class AccountHasBalanceException extends DomainException {

    public AccountHasBalanceException() {
        super("ACCOUNT_HAS_BALANCE", "Account with remaining balance cannot be closed");
    }
}
