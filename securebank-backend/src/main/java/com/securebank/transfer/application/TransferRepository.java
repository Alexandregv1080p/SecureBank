package com.securebank.transfer.application;

import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.IdempotencyKey;
import com.securebank.shared.domain.TransferId;
import com.securebank.transfer.domain.Transfer;
import java.util.List;
import java.util.Optional;

public interface TransferRepository {

    Optional<Transfer> findById(TransferId id);

    boolean existsBySourceAndKey(AccountId sourceAccountId, IdempotencyKey key);

    /** Transferências enviadas pelas contas informadas, da mais recente para a mais antiga. */
    PageResult<Transfer> findBySourceAccounts(List<AccountId> sourceAccountIds, int page, int size);

    void save(Transfer transfer);
}
