package com.securebank.authentication.application;

/** Cifra segredos que precisam ser lidos de volta (segredo TOTP), diferente de senha, que só se verifica. */
public interface SecretCipher {

    String encrypt(String plain);

    String decrypt(String cipherText);
}
