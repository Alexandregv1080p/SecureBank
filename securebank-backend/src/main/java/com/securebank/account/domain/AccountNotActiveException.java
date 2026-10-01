package com.securebank.account.domain;

import com.securebank.shared.domain.DomainException;

public class AccountNotActiveException extends DomainException {

    public AccountNotActiveException(AccountStatus status) {
        super("ACCOUNT_NOT_ACTIVE", "Account is " + status);
    }
}
