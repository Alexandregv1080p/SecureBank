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

    /** Principal (parcelas), encargos por atraso e o já pago de uma fatura. */
    record Totals(Money principal, Money charges, Money paid) {

        public Money total() {
            return principal.plus(charges);
        }
    }

    /** Soma de um tipo de encargo (LATE_FEE ou INTEREST) numa fatura. */
    record Charge(String kind, Money amount) {}

    /** Uma parcela na fatura, com o estabelecimento e o dia da compra. */
    record Item(String merchant, int number, int installments, Money amount, LocalDate purchasedOn) {}

    /** Um pagamento feito numa fatura. */
    record Payment(Money amount, Instant createdAt, String kind) {}

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

    /** Grava o novo limite (a trava das condições já deve estar tomada). */
    void saveLimit(CreditTerms terms);

    void save(CreditPurchase purchase);

    /** Total devido e pago de cada fatura que tem parcela ou pagamento. */
    Map<YearMonth, Totals> totals(CardId cardId, java.util.Currency currency);

    List<Item> items(CardId cardId, YearMonth month, java.util.Currency currency);

    List<Payment> payments(CardId cardId, YearMonth month, java.util.Currency currency);

    /** Encargos de uma fatura, somados por tipo. */
    List<Charge> charges(CardId cardId, YearMonth month, java.util.Currency currency);

    boolean hasCharge(CardId cardId, YearMonth month, String kind);

    /** O último dia de juros já lançado na fatura. */
    Optional<LocalDate> lastInterestDate(CardId cardId, YearMonth month);

    /** Lança um encargo do dia; `false` se esse encargo desse dia já existia (rodar de novo não duplica). */
    boolean saveCharge(CardId cardId, YearMonth month, String kind, Money amount, LocalDate accruedOn, Instant now);

    /** Registra que o aviso saiu; `false` se já tinha saído (cada aviso sai uma vez só). */
    boolean markNotice(CardId cardId, YearMonth month, String kind, LocalDate today);

    /** Cartões que o agendador ainda não processou hoje. */
    List<CardId> dueForBilling(LocalDate today, int limit);

    void markBilled(CardId cardId, LocalDate today);

    /** [transactionId] é nulo no parcelamento da fatura (kind REFINANCE): não sai dinheiro da conta. */
    void savePayment(CardId cardId, YearMonth month, Money amount, TransactionId transactionId, String kind, Instant now);

    PageResult<CreditPurchase> purchases(CardId cardId, int page, int size, java.util.Currency currency);
}
