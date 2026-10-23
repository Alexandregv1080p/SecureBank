package com.securebank.card.domain;

/** Débito: a compra sai do saldo na hora. Crédito: a compra vira parcelas em faturas, pagas depois pela conta. */
public enum CardKind {
    DEBIT, CREDIT
}
