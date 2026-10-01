package com.securebank.transaction.application;

import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.domain.Transaction;
import com.securebank.transaction.domain.TransactionType;
import java.time.Instant;
import java.util.Currency;

public interface TransactionRepository {

    void save(Transaction transaction);

    /** Extrato: lançamentos em [from, to), do mais recente para o mais antigo. */
    PageResult<Transaction> findStatement(AccountId accountId, Instant from, Instant to, int page, int size);

    /** Soma dos débitos concluídos do tipo desde {@code since} (base do limite diário). */
    Money sumCompletedDebitsSince(AccountId accountId, TransactionType type, Instant since, Currency currency);
}
