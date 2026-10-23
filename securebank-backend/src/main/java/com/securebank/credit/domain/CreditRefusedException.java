package com.securebank.credit.domain;

import com.securebank.shared.domain.DomainException;

/** A compra no crédito foi recusada: sem limite ou com fatura vencida. */
public class CreditRefusedException extends DomainException {

    private CreditRefusedException(String code, String message) {
        super(code, message);
    }

    public static CreditRefusedException limitExceeded() {
        return new CreditRefusedException("CREDIT_LIMIT_EXCEEDED", "The purchase is more than the available credit limit");
    }

    public static CreditRefusedException overdue() {
        return new CreditRefusedException("CREDIT_OVERDUE", "Pay the overdue invoice before buying on credit");
    }
}
