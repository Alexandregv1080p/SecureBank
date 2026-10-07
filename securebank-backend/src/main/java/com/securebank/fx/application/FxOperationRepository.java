package com.securebank.fx.application;

import com.securebank.fx.domain.FxOperation;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.CustomerId;

public interface FxOperationRepository {

    void save(FxOperation operation);

    /** Histórico do cliente, mais recente primeiro. */
    PageResult<FxOperation> findByCustomer(CustomerId customerId, int page, int size);
}
