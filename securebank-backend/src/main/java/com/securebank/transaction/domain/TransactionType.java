package com.securebank.transaction.domain;

public enum TransactionType {
    DEPOSIT(TransactionCategory.CASH), WITHDRAW(TransactionCategory.CASH),
    TRANSFER(TransactionCategory.TRANSFERS), PAYMENT(TransactionCategory.PAYMENTS), REFUND(TransactionCategory.PAYMENTS),
    /** Dinheiro guardado num porquinho (débito da conta) e resgatado dele (crédito). */
    PIGGY_IN(TransactionCategory.SAVINGS), PIGGY_OUT(TransactionCategory.SAVINGS),
    /** Pix enviado (débito) e recebido (crédito). */
    PIX_OUT(TransactionCategory.PIX), PIX_IN(TransactionCategory.PIX),
    /** Devolução de Pix: paga por quem recebeu (débito) e recebida por quem enviou (crédito). */
    PIX_RETURN_OUT(TransactionCategory.PIX), PIX_RETURN_IN(TransactionCategory.PIX);

    private final TransactionCategory category;

    TransactionType(TransactionCategory category) {
        this.category = category;
    }

    public TransactionCategory category() {
        return category;
    }
}
