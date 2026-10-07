package com.securebank.fx.application;

import com.securebank.fx.domain.FxRate;
import java.util.List;
import java.util.Optional;

public interface FxRateRepository {

    List<FxRate> findAll();

    Optional<FxRate> findByCurrency(String code);

    /** Atualiza a cotação de uma moeda já suportada. */
    void save(FxRate rate);
}
