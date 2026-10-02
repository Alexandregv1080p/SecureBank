package com.securebank.authentication.application;

/** Resultado de um login/refresh bem-sucedido. O refresh token em claro só existe aqui, na resposta. */
public record TokenPair(String accessToken, long expiresInSeconds, String refreshToken) {}
