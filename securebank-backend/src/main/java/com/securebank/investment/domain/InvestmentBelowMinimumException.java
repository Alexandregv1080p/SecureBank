package com.securebank.investment.domain;

import com.securebank.shared.domain.DomainException;

public class InvestmentBelowMinimumException extends DomainException {

    public InvestmentBelowMinimumException(String minimum) {
        super("INVESTMENT_BELOW_MINIMUM", "The minimum for this product is " + minimum);
    }
}
