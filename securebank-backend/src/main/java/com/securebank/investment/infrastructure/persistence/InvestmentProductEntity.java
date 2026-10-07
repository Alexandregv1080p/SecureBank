package com.securebank.investment.infrastructure.persistence;

import com.securebank.investment.domain.InvestmentKind;
import com.securebank.investment.domain.InvestmentProduct;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "investment_products")
class InvestmentProductEntity {

    @Id String code;
    String name;
    @Enumerated(EnumType.STRING) InvestmentKind kind;
    BigDecimal annualRate;
    Integer termDays;
    BigDecimal minAmount;
    boolean active;

    InvestmentProduct toDomain() {
        return new InvestmentProduct(code, name, kind, annualRate, termDays, minAmount, active);
    }
}
