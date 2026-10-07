package com.securebank.investment.application;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.InvestmentId;

/** Aplicação com prazo que acabou de vencer (ainda sem aviso ao cliente). */
public record MaturedInvestment(InvestmentId id, AccountId accountId, String productName) {}
