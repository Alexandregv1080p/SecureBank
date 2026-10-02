package com.securebank.authentication.domain;

public enum SessionRevocation {
    LOGOUT, USER_REVOKED, PASSWORD_CHANGED, REUSE_DETECTED, USER_DISABLED
}
