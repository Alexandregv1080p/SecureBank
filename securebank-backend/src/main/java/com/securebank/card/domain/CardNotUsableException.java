package com.securebank.card.domain;

import com.securebank.shared.domain.DomainException;

/** O cartão não aceita compra agora: bloqueado, cancelado ou vencido. */
public class CardNotUsableException extends DomainException {

    public CardNotUsableException(String reason) {
        super("CARD_NOT_USABLE", "This card cannot be used: " + reason);
    }
}
