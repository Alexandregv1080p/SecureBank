package com.securebank.investment.domain;

import com.securebank.shared.domain.DomainException;

public class InvestmentNotMaturedException extends DomainException {

    public InvestmentNotMaturedException() {
        super("INVESTMENT_NOT_MATURED", "This investment can only be redeemed at maturity");
    }
}
