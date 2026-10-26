package com.securebank.credit.application;

import com.securebank.card.application.CardRepository;
import com.securebank.card.domain.Card;
import com.securebank.credit.application.CreditRepository.Totals;
import com.securebank.credit.domain.CreditCharges;
import com.securebank.credit.domain.CreditTerms;
import com.securebank.credit.domain.Invoice;
import com.securebank.credit.domain.InvoiceStatus;
import com.securebank.outbox.application.OutboxService;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.Money;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O que acontece com a fatura com o passar dos dias: multa e juros de quem vence sem pagar, e os avisos (fechou, vence em
 * breve, venceu). Roda todo dia pelo agendador, mas é idempotente (cada encargo e cada aviso de um dia só existem uma vez) e
 * também é chamado antes de ler ou pagar uma fatura, então o cliente sempre vê e paga o valor de hoje.
 */
@Service
@Transactional
public class CreditBillingService {

    public static final String LATE_FEE = "LATE_FEE";
    public static final String INTEREST = "INTEREST";
    /** Quantos dias antes do vencimento o aviso "vence em breve" começa. */
    static final int DUE_SOON_DAYS = 3;
    private static final int BATCH = 500;

    private final CreditRepository credit;
    private final CardRepository cards;
    private final OutboxService outbox;
    private final BankTime time;

    public CreditBillingService(CreditRepository credit, CardRepository cards, OutboxService outbox, BankTime time) {
        this.credit = credit;
        this.cards = cards;
        this.outbox = outbox;
        this.time = time;
    }

    /** Cartões que o agendador ainda não processou hoje. */
    @Transactional(readOnly = true)
    public List<CardId> cardsDue(LocalDate today) {
        return credit.dueForBilling(today, BATCH);
    }

    /** Processa um cartão: encargos, avisos e a marca de "feito hoje". Seguro para repetir. */
    public void bill(CardId id, LocalDate today) {
        CreditTerms terms = credit.lockTerms(id).orElseThrow(() -> ApplicationException.notFound("Credit card"));
        accrue(terms, today);
        notices(terms, today);
        credit.markBilled(id, today);
    }

    /** Põe os encargos do cartão em dia até [today]; a trava das condições é tomada aqui. */
    public CreditTerms accrueLocked(CardId id, LocalDate today) {
        CreditTerms terms = credit.lockTerms(id).orElseThrow(() -> ApplicationException.notFound("Credit card"));
        accrue(terms, today);
        return terms;
    }

    /** Multa (uma vez) e juros (um lançamento por dia) das faturas vencidas com saldo. O chamador já tem a trava das condições. */
    public void accrue(CreditTerms terms, LocalDate today) {
        CardId id = terms.cardId();
        java.util.Currency currency = terms.limit().currency();
        Instant now = time.now();
        for (Map.Entry<YearMonth, Totals> entry : credit.totals(id, currency).entrySet()) {
            YearMonth month = entry.getKey();
            Totals t = entry.getValue();
            LocalDate due = terms.dueDate(month);
            if (!t.principal().isPositive() || !today.isAfter(due)) {
                continue;
            }
            Money remaining = t.total().minus(t.paid());
            if (!remaining.isPositive()) {
                continue;
            }
            Money room = CreditCharges.room(t.principal(), t.charges()); // teto: encargos até 100% do principal
            if (!credit.hasCharge(id, month, LATE_FEE) && t.paid().isLessThan(Invoice.minimumOf(t.principal()))) {
                Money unpaid = t.principal().minus(t.paid().isGreaterThan(t.principal()) ? t.principal() : t.paid());
                Money fee = CreditCharges.capped(CreditCharges.lateFee(unpaid), room);
                if (fee.isPositive() && credit.saveCharge(id, month, LATE_FEE, fee, due.plusDays(1), now)) {
                    remaining = remaining.plus(fee);
                    room = room.minus(fee);
                }
            }
            LocalDate from = credit.lastInterestDate(id, month).orElse(due);
            for (LocalDate day = from.plusDays(1); !day.isAfter(today); day = day.plusDays(1)) {
                if (!room.isPositive()) {
                    break;
                }
                Money interest = CreditCharges.capped(CreditCharges.dailyInterest(remaining), room);
                if (interest.isPositive() && credit.saveCharge(id, month, INTEREST, interest, day, now)) {
                    remaining = remaining.plus(interest);
                    room = room.minus(interest);
                }
            }
        }
    }

    private void notices(CreditTerms terms, LocalDate today) {
        CardId id = terms.cardId();
        Card card = cards.findById(id).orElse(null);
        if (card == null) {
            return;
        }
        java.util.Currency currency = terms.limit().currency();
        for (Map.Entry<YearMonth, Totals> entry : credit.totals(id, currency).entrySet()) {
            YearMonth month = entry.getKey();
            Totals t = entry.getValue();
            if (!t.principal().isPositive()) {
                continue;
            }
            Invoice invoice = Invoice.of(terms, month, t.principal(), t.charges(), t.paid(), today);
            if (invoice.status() == InvoiceStatus.PAID) {
                continue;
            }
            LocalDate due = invoice.dueDate();
            if (today.isAfter(invoice.closingDate()) && !today.isAfter(due) && credit.markNotice(id, month, "CLOSED", today)) {
                publish("CreditInvoiceClosed", card, invoice);
            }
            if (!today.isBefore(due.minusDays(DUE_SOON_DAYS)) && !today.isAfter(due) && credit.markNotice(id, month, "DUE_SOON", today)) {
                publish("CreditInvoiceDueSoon", card, invoice);
            }
            if (today.isAfter(due) && credit.markNotice(id, month, "OVERDUE", today)) {
                publish("CreditInvoiceOverdue", card, invoice);
            }
        }
    }

    private void publish(String type, Card card, Invoice invoice) {
        outbox.record(type, "Card", card.id().toString(), Map.of(
                "cardId", card.id().toString(), "accountId", card.accountId().toString(), "last4", card.last4(),
                "month", invoice.month().toString(), "amount", invoice.remaining().amount().toPlainString(),
                "minimum", invoice.minimumDue().amount().toPlainString(), "currency", invoice.principal().currency().getCurrencyCode(),
                "dueDate", invoice.dueDate().toString(), "revolving", String.valueOf(invoice.status() == InvoiceStatus.REVOLVING)));
    }
}
