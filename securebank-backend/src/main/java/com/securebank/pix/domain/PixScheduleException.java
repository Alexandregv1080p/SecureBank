package com.securebank.pix.domain;

import com.securebank.shared.domain.DomainException;

public class PixScheduleException extends DomainException {

    private PixScheduleException(String code, String message) {
        super(code, message);
    }

    public static PixScheduleException notCancelable() {
        return new PixScheduleException("PIX_SCHEDULE_NOT_CANCELABLE", "Only a scheduled Pix can be canceled");
    }
}
