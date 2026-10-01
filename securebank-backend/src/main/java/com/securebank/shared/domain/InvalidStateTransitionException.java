package com.securebank.shared.domain;

public class InvalidStateTransitionException extends DomainException {

    public InvalidStateTransitionException(String entity, Enum<?> from, Enum<?> to) {
        super("INVALID_STATE_TRANSITION", entity + " cannot go from " + from + " to " + to);
    }
}
