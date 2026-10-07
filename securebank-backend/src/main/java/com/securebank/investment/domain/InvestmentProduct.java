package com.securebank.investment.domain;

import java.math.BigDecimal;

/**
 * Produto de renda fixa simulado (tipo CDB). [annualRate] é a taxa efetiva ao ano (0.1050 = 10,50% a.a.). A aplicação
 * guarda uma cópia da taxa e do prazo: mudar o produto depois não altera quem já aplicou.
 */
public record InvestmentProduct(String code, String name, InvestmentKind kind, BigDecimal annualRate, Integer termDays,
        BigDecimal minAmount, boolean active) {}
