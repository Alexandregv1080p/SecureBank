package com.securebank.credit.domain;

/** OPEN: ainda recebe compras. CLOSED: fechou, aguarda o pagamento. OVERDUE: passou do vencimento com saldo. PAID: quitada. */
public enum InvoiceStatus {
    OPEN, CLOSED, OVERDUE, PAID
}
