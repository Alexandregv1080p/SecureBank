package com.securebank.credit.application;

import com.securebank.card.application.CardCancellationGuard;
import com.securebank.card.domain.Card;
import com.securebank.card.domain.CardKind;
import com.securebank.shared.application.ApplicationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Cartão de crédito com dívida não pode ser cancelado: a fatura precisa ser quitada antes. */
@Component
@Transactional(readOnly = true)
class CreditCancellationGuard implements CardCancellationGuard {

    private final CreditRepository credit;

    CreditCancellationGuard(CreditRepository credit) {
        this.credit = credit;
    }

    @Override
    public void check(Card card) {
        if (card.kind() == CardKind.CREDIT && credit.hasActiveAdditional(card.id())) {
            throw ApplicationException.unprocessable("CARD_HAS_ADDITIONAL", "Cancel the additional cards before canceling the main card");
        }
        if (card.kind() == CardKind.CREDIT && credit.outstanding(card.id()).isPositive()) {
            throw ApplicationException.unprocessable("CARD_HAS_DEBT", "Pay the card's invoices before canceling it");
        }
    }
}
