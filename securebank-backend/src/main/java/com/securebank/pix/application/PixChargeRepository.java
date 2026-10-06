package com.securebank.pix.application;

import com.securebank.pix.domain.PixCharge;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.CustomerId;
import java.util.Optional;

public interface PixChargeRepository {

    Optional<PixCharge> findByTxid(String txid);

    /** Cobranças que o cliente criou, da mais nova para a mais antiga. */
    PageResult<PixCharge> findByCustomer(CustomerId customerId, int page, int size);

    /** A versão otimista é conferida no flush: dois pagamentos simultâneos não passam os dois. */
    void save(PixCharge charge);
}
