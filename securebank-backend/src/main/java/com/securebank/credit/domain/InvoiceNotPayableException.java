package com.securebank.credit.domain;

import com.securebank.shared.domain.DomainException;

public class InvoiceNotPayableException extends DomainException {

    public InvoiceNotPayableException(String message) {
        super("INVOICE_NOT_PAYABLE", message);
    }
}
