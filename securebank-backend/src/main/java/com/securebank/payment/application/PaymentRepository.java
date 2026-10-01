package com.securebank.payment.application;

import com.securebank.payment.domain.Payment;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.IdempotencyKey;
import com.securebank.shared.domain.PaymentId;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {

    Optional<Payment> findById(PaymentId id);

    boolean existsByAccountAndKey(AccountId accountId, IdempotencyKey key);

    PageResult<Payment> findByAccounts(List<AccountId> accountIds, int page, int size);

    void save(Payment payment);
}
