package com.securebank.shared.infrastructure.web;

import com.securebank.shared.domain.Money;

/** Dinheiro na API: valor como string ("100.00") para nenhum cliente JSON perder precisão com ponto flutuante. */
public record MoneyResponse(String amount, String currency) {

    public static MoneyResponse of(Money money) {
        return new MoneyResponse(money.amount().toPlainString(), money.currency().getCurrencyCode());
    }
}
