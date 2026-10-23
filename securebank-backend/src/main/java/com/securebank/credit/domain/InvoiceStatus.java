package com.securebank.credit.domain;

/**
 * OPEN: ainda recebe compras. CLOSED: fechou, aguarda o pagamento. PAID: quitada. Vencida com saldo: REVOLVING se o
 * pagamento mínimo foi feito (o resto rende juros) ou OVERDUE se não foi (multa, juros e compras bloqueadas).
 */
public enum InvoiceStatus {
    OPEN, CLOSED, OVERDUE, REVOLVING, PAID
}
