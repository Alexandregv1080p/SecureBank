package com.securebank.card.domain;

import com.securebank.account.domain.Account;
import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.domain.Transaction;
import java.time.Instant;
import java.time.LocalDate;

/** Efetiva uma compra no débito: confere o cartão, o limite e o saldo antes de mutar e debita a conta. */
public final class CardPurchaseService {

    /** @param usedToday soma das compras no débito já efetivadas hoje pela conta */
    public Transaction purchase(Card card, Account account, Limit cardLimit, Money usedToday, Money amount,
            LocalDate today, Instant now, String reference) {
        if (!account.id().equals(card.accountId()) || !cardLimit.accountId().equals(account.id())
                || cardLimit.type() != LimitType.CARD) {
            throw new InvalidValueException("Account and limit do not match the card");
        }
        card.ensureCanPurchase(today);
        cardLimit.check(amount, usedToday);
        account.ensureCanDebit(amount);
        return account.cardPurchase(amount, reference, now);
    }
}
