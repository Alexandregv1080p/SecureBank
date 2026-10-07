package com.securebank.fx.application;

import com.securebank.fx.domain.FxWallet;
import com.securebank.shared.domain.CustomerId;
import java.util.List;
import java.util.Optional;

public interface FxWalletRepository {

    Optional<FxWallet> find(CustomerId customerId, String currencyCode);

    List<FxWallet> findByCustomer(CustomerId customerId);

    /** A versão otimista é conferida no flush: dois movimentos simultâneos na mesma carteira não passam os dois. */
    void save(FxWallet wallet);
}
