package com.securebank.pix.application;

import com.securebank.pix.domain.PixTransfer;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.PixTransferId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PixTransferRepository {

    void save(PixTransfer pix);

    Optional<PixTransfer> findById(PixTransferId id);

    /** Quanto já foi devolvido de cada Pix (só os que têm devolução aparecem no mapa). */
    Map<PixTransferId, BigDecimal> refundedAmounts(List<PixTransferId> originals);

    /** Pix enviados OU recebidos por qualquer uma das contas, do mais recente para o mais antigo. */
    PageResult<PixTransfer> findByAccounts(List<AccountId> accountIds, int page, int size);
}
