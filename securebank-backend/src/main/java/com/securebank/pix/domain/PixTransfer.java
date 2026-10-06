package com.securebank.pix.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PixTransferId;
import com.securebank.shared.domain.TransactionId;
import java.time.Duration;
import java.time.Instant;

/**
 * Um Pix efetivado entre duas contas. Só existem Pix concluídos (tentativa recusada não deixa registro aqui; fica na
 * auditoria). Guarda a chave usada e os nomes JÁ mascarados dos dois lados, para o histórico de cada um mostrar a
 * contraparte sem consultar o cadastro do outro cliente.
 */
public final class PixTransfer {

    public static final int MESSAGE_MAX = 140;
    /** Prazo do Banco Central para devolver um Pix recebido. */
    public static final int REFUND_WINDOW_DAYS = 90;

    private final PixTransferId id;
    private final AccountId sourceAccountId;
    private final AccountId destinationAccountId;
    private final Money amount;
    private final String message;
    private final String destinationKey;
    private final String sourceName;
    private final String destinationName;
    private final String endToEndId;
    private TransactionId debitTransactionId;
    private TransactionId creditTransactionId;
    private final PixTransferId refundOfId;
    private String chargeTxid;
    private final Instant createdAt;

    private PixTransfer(PixTransferId id, AccountId source, AccountId destination, Money amount, String message,
            String destinationKey, String sourceName, String destinationName, String endToEndId, PixTransferId refundOfId,
            Instant createdAt) {
        this.id = id;
        this.sourceAccountId = source;
        this.destinationAccountId = destination;
        this.amount = amount;
        this.message = message;
        this.destinationKey = destinationKey;
        this.sourceName = sourceName;
        this.destinationName = destinationName;
        this.endToEndId = endToEndId;
        this.refundOfId = refundOfId;
        this.createdAt = createdAt;
    }

    public static PixTransfer create(AccountId source, AccountId destination, Money amount, String message,
            String destinationKey, String sourceName, String destinationName, String endToEndId, Instant now) {
        if (source == null || destination == null || amount == null || destinationKey == null || endToEndId == null
                || now == null) {
            throw new InvalidValueException("Pix requires source, destination, amount, key, end-to-end id and time");
        }
        if (source.equals(destination)) {
            throw new InvalidValueException("Source and destination accounts must be different");
        }
        if (!amount.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
        String text = message == null || message.isBlank() ? null : message.trim();
        if (text != null && text.length() > MESSAGE_MAX) {
            throw new InvalidValueException("Message must have at most " + MESSAGE_MAX + " characters");
        }
        return new PixTransfer(PixTransferId.newId(), source, destination, amount, text, destinationKey,
                sourceName == null ? "" : sourceName, destinationName == null ? "" : destinationName, endToEndId, null, now);
    }

    /**
     * Devolução (total ou parcial) de um Pix RECEBIDO: vai no sentido contrário, da conta que recebeu para a que enviou.
     * As regras (prazo, valor, quem pode) são validadas pelo {@link PixService#refund}.
     */
    public static PixTransfer refundOf(PixTransfer original, Money amount, String endToEndId, Instant now) {
        if (original == null || amount == null || endToEndId == null || now == null) {
            throw new InvalidValueException("Refund requires the original Pix, amount, end-to-end id and time");
        }
        if (!amount.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
        return new PixTransfer(PixTransferId.newId(), original.destinationAccountId, original.sourceAccountId, amount,
                "Devolução", original.destinationKey, original.destinationName, original.sourceName, endToEndId,
                original.id, now);
    }

    public static PixTransfer restore(PixTransferId id, AccountId source, AccountId destination, Money amount,
            String message, String destinationKey, String sourceName, String destinationName, String endToEndId,
            TransactionId debit, TransactionId credit, PixTransferId refundOfId, String chargeTxid, Instant createdAt) {
        PixTransfer pix = new PixTransfer(id, source, destination, amount, message, destinationKey, sourceName,
                destinationName, endToEndId, refundOfId, createdAt);
        pix.debitTransactionId = debit;
        pix.creditTransactionId = credit;
        pix.chargeTxid = chargeTxid;
        return pix;
    }

    /** Liga o Pix aos dois lançamentos que ele gerou (débito na origem, crédito no destino). */
    public void settle(TransactionId debit, TransactionId credit) {
        if (debitTransactionId != null) {
            throw new InvalidValueException("Pix already settled");
        }
        this.debitTransactionId = debit;
        this.creditTransactionId = credit;
    }

    /** Marca este Pix como o pagamento de uma cobrança (feito uma vez, antes de gravar). */
    public void linkToCharge(String txid) {
        if (chargeTxid != null || txid == null) {
            throw new InvalidValueException("Pix already linked to a charge");
        }
        this.chargeTxid = txid;
    }

    public boolean isRefund() {
        return refundOfId != null;
    }

    /**
     * Quanto ainda pode ser devolvido deste Pix: o valor menos o que já foi devolvido, ou zero se este Pix é uma
     * devolução ou o prazo acabou.
     */
    public Money refundable(Money alreadyRefunded, Instant now) {
        if (isRefund() || windowClosed(now)) {
            return Money.zero(amount.currency());
        }
        Money remaining = amount.minus(alreadyRefunded);
        return remaining.isPositive() ? remaining : Money.zero(amount.currency());
    }

    boolean windowClosed(Instant now) {
        return now.isAfter(createdAt.plus(Duration.ofDays(REFUND_WINDOW_DAYS)));
    }

    public PixTransferId id() { return id; }
    public AccountId sourceAccountId() { return sourceAccountId; }
    public AccountId destinationAccountId() { return destinationAccountId; }
    public Money amount() { return amount; }
    public String message() { return message; }
    public String destinationKey() { return destinationKey; }
    public String sourceName() { return sourceName; }
    public String destinationName() { return destinationName; }
    public String endToEndId() { return endToEndId; }
    public TransactionId debitTransactionId() { return debitTransactionId; }
    public TransactionId creditTransactionId() { return creditTransactionId; }
    public PixTransferId refundOfId() { return refundOfId; }
    public String chargeTxid() { return chargeTxid; }
    public Instant createdAt() { return createdAt; }
}
