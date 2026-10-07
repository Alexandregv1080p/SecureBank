package com.securebank.fx.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Registro (recibo) de uma compra ou venda de moeda: o que foi trocado, a que cotação e quanto saiu/entrou em reais. */
public record FxOperation(UUID id, CustomerId customerId, AccountId accountId, FxSide side, Money foreignAmount,
        BigDecimal rate, Money brlAmount, Instant createdAt) {}
