package com.securebank.pix.domain;

import com.securebank.account.domain.Account;
import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.domain.Transaction;
import java.time.Instant;

/**
 * Regra de um Pix, que envolve duas contas e o limite Pix da origem. Valida TUDO antes de mutar qualquer coisa:
 * se algo falhar, nenhuma das duas contas fica alterada. Persistir na mesma transação é da camada de aplicação.
 */
public final class PixService {

    public record Result(Transaction debit, Transaction credit) {}

    /** @param usedToday soma dos Pix já enviados hoje pela conta de origem */
    public Result execute(PixTransfer pix, Account source, Account destination, Limit pixLimit, Money usedToday,
            Instant now) {
        if (!source.id().equals(pix.sourceAccountId()) || !destination.id().equals(pix.destinationAccountId())
                || !pixLimit.accountId().equals(source.id()) || pixLimit.type() != LimitType.PIX) {
            throw new InvalidValueException("Accounts and limit do not match the Pix");
        }
        Money amount = pix.amount();

        pixLimit.check(amount, usedToday);
        source.ensureCanDebit(amount);
        destination.ensureCanCredit(amount);

        String reference = pix.endToEndId();
        Transaction debit = source.pixOut(amount, reference, now);
        Transaction credit = destination.pixIn(amount, reference, now);
        pix.settle(debit.id(), credit.id());
        return new Result(debit, credit);
    }

    /**
     * Devolução: a conta que RECEBEU o Pix original devolve (parte de) o valor à que enviou. Regras: o original não pode
     * ser, ele mesmo, uma devolução; vale até 90 dias; a soma devolvida não passa do valor recebido. Não consome o limite
     * diário do Pix. Valida tudo antes de mutar qualquer conta.
     *
     * @param receiver conta que recebeu o original (e agora paga a devolução)
     * @param payer conta que enviou o original (e agora recebe)
     */
    public Result refund(PixTransfer original, PixTransfer refund, Account receiver, Account payer,
            Money alreadyRefunded, Instant now) {
        if (!receiver.id().equals(original.destinationAccountId()) || !payer.id().equals(original.sourceAccountId())
                || !refund.sourceAccountId().equals(receiver.id()) || !refund.destinationAccountId().equals(payer.id())
                || !original.id().equals(refund.refundOfId())) {
            throw new InvalidValueException("Accounts do not match the refund");
        }
        if (original.isRefund()) {
            throw PixRefundException.notRefundable();
        }
        if (original.windowClosed(now)) {
            throw PixRefundException.expired();
        }
        if (refund.amount().isGreaterThan(original.refundable(alreadyRefunded, now))) {
            throw PixRefundException.exceeds();
        }
        receiver.ensureCanDebit(refund.amount());
        payer.ensureCanCredit(refund.amount());

        Transaction debit = receiver.pixReturnOut(refund.amount(), refund.endToEndId(), now);
        Transaction credit = payer.pixReturnIn(refund.amount(), refund.endToEndId(), now);
        refund.settle(debit.id(), credit.id());
        return new Result(debit, credit);
    }
}
