package com.securebank.pix.domain;

import com.securebank.shared.domain.DomainException;

/** Devolução recusada pelas regras do Pix (prazo, valor ou tipo de Pix). */
public class PixRefundException extends DomainException {

    private PixRefundException(String code, String message) {
        super(code, message);
    }

    public static PixRefundException expired() {
        return new PixRefundException("PIX_REFUND_EXPIRED",
                "A refund can only be made within " + PixTransfer.REFUND_WINDOW_DAYS + " days of the Pix");
    }

    public static PixRefundException exceeds() {
        return new PixRefundException("PIX_REFUND_EXCEEDS", "The refund is greater than what can still be returned");
    }

    public static PixRefundException notRefundable() {
        return new PixRefundException("PIX_NOT_REFUNDABLE", "A refund cannot be refunded again");
    }
}
