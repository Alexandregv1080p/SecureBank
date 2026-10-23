package com.securebank.card.application;

import com.securebank.card.domain.Card;

/**
 * Quem tem dados ligados ao cartão (como a dívida do crédito) pode vetar o cancelamento: lança uma ApplicationException
 * com o motivo. Fica aqui para o módulo de cartão não depender dos módulos que o usam.
 */
public interface CardCancellationGuard {

    void check(Card card);
}
