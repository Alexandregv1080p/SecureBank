package com.securebank.investment.application;

import com.securebank.investment.domain.InvestmentProduct;
import java.util.List;
import java.util.Optional;

public interface InvestmentProductRepository {

    List<InvestmentProduct> findActive();

    Optional<InvestmentProduct> findByCode(String code);
}
