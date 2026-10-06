package com.securebank.pix.application;

import com.securebank.pix.domain.PixTransfer;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import java.util.List;

public interface PixTransferRepository {

    void save(PixTransfer pix);

    /** Pix enviados OU recebidos por qualquer uma das contas, do mais recente para o mais antigo. */
    PageResult<PixTransfer> findByAccounts(List<AccountId> accountIds, int page, int size);
}
