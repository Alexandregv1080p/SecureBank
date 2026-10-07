package com.securebank.investment.domain;

import com.securebank.shared.domain.DomainException;

public class InvestmentAlreadyRedeemedException extends DomainException {

    public InvestmentAlreadyRedeemedException() {
        super("INVESTMENT_ALREADY_REDEEMED", "This investment was already redeemed");
    }
}
