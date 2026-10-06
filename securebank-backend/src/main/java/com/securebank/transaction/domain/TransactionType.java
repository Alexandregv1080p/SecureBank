package com.securebank.transaction.domain;

public enum TransactionType {
    DEPOSIT, WITHDRAW, TRANSFER, PAYMENT, REFUND,
    /** Dinheiro guardado num porquinho (débito da conta) e resgatado dele (crédito). */
    PIGGY_IN, PIGGY_OUT,
    /** Pix enviado (débito) e recebido (crédito). */
    PIX_OUT, PIX_IN,
    /** Devolução de Pix: paga por quem recebeu (débito) e recebida por quem enviou (crédito). */
    PIX_RETURN_OUT, PIX_RETURN_IN
}
