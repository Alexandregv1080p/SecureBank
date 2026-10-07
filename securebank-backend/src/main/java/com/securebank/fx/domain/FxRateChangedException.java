package com.securebank.fx.domain;

import com.securebank.shared.domain.DomainException;

public class FxRateChangedException extends DomainException {

    public FxRateChangedException() {
        super("FX_RATE_CHANGED", "The exchange rate changed; review the new quote and try again");
    }
}
