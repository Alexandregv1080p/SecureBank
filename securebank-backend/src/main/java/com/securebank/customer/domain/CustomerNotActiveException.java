package com.securebank.customer.domain;

import com.securebank.shared.domain.DomainException;

public class CustomerNotActiveException extends DomainException {

    public CustomerNotActiveException(CustomerStatus status) {
        super("CUSTOMER_NOT_ACTIVE", "Customer is " + status);
    }
}
