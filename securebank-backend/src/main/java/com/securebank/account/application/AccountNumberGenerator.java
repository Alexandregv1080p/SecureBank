package com.securebank.account.application;

import com.securebank.account.domain.AccountNumber;

public interface AccountNumberGenerator {

    AccountNumber next();
}
