package com.securebank.payment.domain;

import com.securebank.account.domain.Account;
import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.domain.Transaction;
import java.time.Instant;

/** Efetiva um pagamento: valida limite e saldo antes de mutar, debita a conta e conclui o Payment. */
public final class PaymentService {

    /** @param usedToday soma dos pagamentos já efetivados hoje pela conta */
    public Transaction execute(Payment payment, Account account, Limit paymentLimit, Money usedToday, Instant now) {
        if (!account.id().equals(payment.accountId()) || !paymentLimit.accountId().equals(account.id())
                || paymentLimit.type() != LimitType.PAYMENT) {
            throw new InvalidValueException("Account and limit do not match the payment");
        }

        paymentLimit.check(payment.amount(), usedToday);
        account.ensureCanDebit(payment.amount());

        payment.markProcessing(now); // falha aqui (pagamento já processado) antes de qualquer débito
        Transaction transaction = account.pay(payment.amount(), payment.id().toString(), now);
        payment.complete(transaction.id(), now);
        return transaction;
    }
}
