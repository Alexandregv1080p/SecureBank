package com.securebank.transfer.domain;

import com.securebank.account.domain.Account;
import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.domain.Transaction;
import java.time.Instant;

/**
 * Regra de uma transferência que envolve dois aggregates (Account origem + Account destino) e o Limit.
 * Valida TUDO antes de mutar qualquer coisa: se algo falhar, nenhuma das duas contas fica alterada.
 * Quem carrega os aggregates, trava as linhas e persiste o resultado na mesma transação é a camada de aplicação.
 */
public final class TransferService {

    public record Result(Transaction debit, Transaction credit) {}

    /** @param usedToday soma das transferências já efetivadas hoje pela conta de origem */
    public Result execute(Transfer transfer, Account source, Account destination, Limit transferLimit,
            Money usedToday, Instant now) {
        if (!source.id().equals(transfer.sourceAccountId()) || !destination.id().equals(transfer.destinationAccountId())
                || !transferLimit.accountId().equals(source.id()) || transferLimit.type() != LimitType.TRANSFER) {
            throw new InvalidValueException("Accounts and limit do not match the transfer");
        }
        Money amount = transfer.amount();

        transfer.ensurePending();
        transferLimit.check(amount, usedToday);
        source.ensureCanDebit(amount);
        destination.ensureCanCredit(amount);

        String reference = transfer.id().toString();
        Transaction debit = source.transferOut(amount, reference, now);
        Transaction credit = destination.transferIn(amount, reference, now);
        transfer.complete(debit.id(), credit.id(), now);
        return new Result(debit, credit);
    }
}
