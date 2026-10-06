package com.securebank.pix.domain;

import com.securebank.shared.domain.DomainException;

public class PixChargeException extends DomainException {

    private PixChargeException(String code, String message) {
        super(code, message);
    }

    public static PixChargeException expired() {
        return new PixChargeException("PIX_CHARGE_EXPIRED", "This Pix charge has expired");
    }

    public static PixChargeException notPayable(PixChargeStatus status) {
        return new PixChargeException("PIX_CHARGE_NOT_PAYABLE", "This Pix charge is " + status.name().toLowerCase());
    }

    public static PixChargeException notCancelable() {
        return new PixChargeException("PIX_CHARGE_NOT_CANCELABLE", "Only an active Pix charge can be canceled");
    }
}
