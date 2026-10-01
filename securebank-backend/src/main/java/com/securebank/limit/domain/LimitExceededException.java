package com.securebank.limit.domain;

import com.securebank.shared.domain.DomainException;

public class LimitExceededException extends DomainException {

    public enum Scope { PER_OPERATION, DAILY }

    private final Scope scope;

    public LimitExceededException(LimitType type, Scope scope) {
        super("LIMIT_EXCEEDED", type + " " + scope.name().toLowerCase().replace('_', ' ') + " limit exceeded");
        this.scope = scope;
    }

    public Scope scope() {
        return scope;
    }
}
