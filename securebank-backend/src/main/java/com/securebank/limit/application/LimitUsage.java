package com.securebank.limit.application;

import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.application.TransactionRepository;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Junta o limite da conta com o que já foi consumido hoje (dia de America/Sao_Paulo). */
@Component
@Transactional(readOnly = true)
public class LimitUsage {

    /** Limite com o quanto já foi consumido hoje. */
    public record Status(Limit limit, Money usedToday) {

        public Money remainingToday() {
            Money remaining = limit.daily().minus(usedToday);
            return remaining.isPositive() ? remaining : Money.zero(remaining.currency());
        }

        /** Lança LimitExceededException se a operação estourar o teto por operação ou o diário. */
        public void check(Money amount) {
            limit.check(amount, usedToday);
        }
    }

    private final LimitRepository limits;
    private final TransactionRepository transactions;
    private final BankTime time;

    public LimitUsage(LimitRepository limits, TransactionRepository transactions, BankTime time) {
        this.limits = limits;
        this.transactions = transactions;
        this.time = time;
    }

    public Status of(AccountId accountId, LimitType type, Currency currency) {
        Limit limit = limits.find(accountId, type)
                .orElseThrow(() -> new IllegalStateException("Account " + accountId + " has no " + type + " limit"));
        return status(limit, currency);
    }

    public List<Status> allOf(AccountId accountId, Currency currency) {
        return limits.findAll(accountId).stream()
                .sorted(Comparator.comparing(Limit::type))
                .map(limit -> status(limit, currency))
                .toList();
    }

    private Status status(Limit limit, Currency currency) {
        Money used = transactions.sumCompletedDebitsSince(limit.accountId(), limit.type().transactionType(),
                time.startOfToday(), currency);
        return new Status(limit, used);
    }
}
