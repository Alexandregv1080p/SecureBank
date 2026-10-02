package com.securebank.authentication.application;

/** Porta de hashing de senha (Argon2id na implementação). O hash carrega algoritmo e parâmetros. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
