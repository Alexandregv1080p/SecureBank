package com.securebank.credit.application;

import com.securebank.credit.domain.CreditPurchase;
import com.securebank.credit.domain.CreditTerms;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.TransactionId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CreditRepository {

    /** Total das parcelas e o já pago de uma fatura. */
    record Totals(Money total, Money paid) {}

    /** Uma parcela na fatura, com o estabelecimento e o dia da compra. */
    record Item(String merchant, int number, int installments, Money amount, LocalDate purchasedOn) {}

    /** Um pagamento feito numa fatura. */
    record Payment(Money amount, Instant createdAt) {}

    Optional<CreditTerms> terms(CardId cardId);

    /** Dívida total do cartão: parcelas de todas as faturas menos o que já foi pago (zero se não é crédito). */
    default Money outstanding(CardId cardId) {
        return terms(cardId).map(t -> sumOpen(totals(cardId, t.limit().currency()), t.limit().currency())).orElse(Money.zero(Money.BRL));
    }

    static Money sumOpen(Map<YearMonth, Totals> totals, java.util.Currency currency) {
        Money used = Money.zero(currency);
        for (Totals t : totals.values()) {
            Money open = t.total().minus(t.paid());
            if (open.isPositive()) {
                used = used.plus(open);
            }
        }
        return used;
    }

    /** As condições com trava de escrita: compras e pagamentos do mesmo cartão se enfileiram (o limite não estoura em corrida). */
    Optional<CreditTerms> lockTerms(CardId cardId);

    void insert(CreditTerms terms);

    void save(CreditPurchase purchase);

    /** Total devido e pago de cada fatura que tem parcela ou pagamento. */
    Map<YearMonth, Totals> totals(CardId cardId, java.util.Currency currency);

    List<Item> items(CardId cardId, YearMonth month, java.util.Currency currency);

    List<Payment> payments(CardId cardId, YearMonth month, java.util.Currency currency);

    void savePayment(CardId cardId, YearMonth month, Money amount, TransactionId transactionId, Instant now);

    PageResult<CreditPurchase> purchases(CardId cardId, int page, int size, java.util.Currency currency);
}
