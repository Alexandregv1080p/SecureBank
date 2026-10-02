package com.securebank.authentication.application;

/** Login em duas etapas: ou já autenticado, ou falta o código MFA. */
public sealed interface LoginResult {

    record Authenticated(TokenPair tokens) implements LoginResult {}

    record MfaRequired(String mfaToken, long expiresInSeconds) implements LoginResult {}
}
