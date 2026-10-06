package com.securebank.pix.domain;

import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.PixScheduleId;
import com.securebank.shared.domain.PixTransferId;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Pix agendado: a autorização é dada AGORA (quem agenda confirma a identidade), mas o dinheiro só se move na data. Na
 * data valem as regras do dia: saldo, limite diário do Pix e a chave (que pode ter mudado de dono ou sumido). Se algo
 * impedir, o agendamento vira FAILED com o motivo e o cliente é avisado; ele nunca é repetido sozinho.
 */
public final class PixSchedule {

    public static final int MESSAGE_MAX = 140;
    public static final int MAX_DAYS_AHEAD = 365;
    static final int REASON_MAX = 60;

    private final PixScheduleId id;
    private final CustomerId customerId;
    private final AccountId sourceAccountId;
    private final String key;
    private final String destinationName;
    private final Money amount;
    private final String message;
    private final LocalDate scheduledFor;
    private PixScheduleStatus status;
    private String failureReason;
    private PixTransferId executedPixId;
    private final Instant createdAt;
    private Instant updatedAt;

    private PixSchedule(PixScheduleId id, CustomerId customerId, AccountId sourceAccountId, String key,
            String destinationName, Money amount, String message, LocalDate scheduledFor, PixScheduleStatus status,
            Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.customerId = customerId;
        this.sourceAccountId = sourceAccountId;
        this.key = key;
        this.destinationName = destinationName;
        this.amount = amount;
        this.message = message;
        this.scheduledFor = scheduledFor;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * @param key chave já resolvida (valor canônico) e {@code destinationName} já mascarado, como no envio imediato
     * @param today dia atual de America/Sao_Paulo: só se agenda do dia seguinte em diante, até 365 dias
     */
    public static PixSchedule create(CustomerId customerId, AccountId sourceAccountId, String key, String destinationName,
            Money amount, String message, LocalDate scheduledFor, LocalDate today, Instant now) {
        if (customerId == null || sourceAccountId == null || key == null || key.isBlank() || amount == null
                || scheduledFor == null || today == null || now == null) {
            throw new InvalidValueException("Schedule requires customer, account, key, amount, date and time");
        }
        if (!amount.isPositive()) {
            throw new InvalidValueException("Amount must be greater than zero");
        }
        if (!scheduledFor.isAfter(today)) {
            throw new InvalidValueException("The date must be after today");
        }
        if (scheduledFor.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new InvalidValueException("The date must be within " + MAX_DAYS_AHEAD + " days");
        }
        String text = message == null || message.isBlank() ? null : message.trim();
        if (text != null && text.length() > MESSAGE_MAX) {
            throw new InvalidValueException("Message must have at most " + MESSAGE_MAX + " characters");
        }
        return new PixSchedule(PixScheduleId.newId(), customerId, sourceAccountId, key,
                destinationName == null ? "" : destinationName, amount, text, scheduledFor, PixScheduleStatus.SCHEDULED, now, now);
    }

    public static PixSchedule restore(PixScheduleId id, CustomerId customerId, AccountId sourceAccountId, String key,
            String destinationName, Money amount, String message, LocalDate scheduledFor, PixScheduleStatus status,
            String failureReason, PixTransferId executedPixId, Instant createdAt, Instant updatedAt) {
        PixSchedule schedule = new PixSchedule(id, customerId, sourceAccountId, key, destinationName, amount, message,
                scheduledFor, status, createdAt, updatedAt);
        schedule.failureReason = failureReason;
        schedule.executedPixId = executedPixId;
        return schedule;
    }

    /** Já é a data (ou passou) e ainda não foi feito? */
    public boolean isDueOn(LocalDate today) {
        return status == PixScheduleStatus.SCHEDULED && !scheduledFor.isAfter(today);
    }

    public void markExecuted(PixTransferId pixId, Instant now) {
        transitionTo(PixScheduleStatus.EXECUTED, now);
        this.executedPixId = pixId;
    }

    public void markFailed(String reason, Instant now) {
        transitionTo(PixScheduleStatus.FAILED, now);
        String text = reason == null ? "UNKNOWN" : reason;
        this.failureReason = text.length() > REASON_MAX ? text.substring(0, REASON_MAX) : text;
    }

    public void cancel(Instant now) {
        if (status != PixScheduleStatus.SCHEDULED) {
            throw PixScheduleException.notCancelable();
        }
        transitionTo(PixScheduleStatus.CANCELED, now);
    }

    private void transitionTo(PixScheduleStatus target, Instant now) {
        if (status != PixScheduleStatus.SCHEDULED) {
            throw new InvalidStateTransitionException("PixSchedule", status, target);
        }
        this.status = target;
        this.updatedAt = now;
    }

    public boolean isOwnedBy(CustomerId customerId) {
        return this.customerId.equals(customerId);
    }

    public PixScheduleId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public AccountId sourceAccountId() { return sourceAccountId; }
    public String key() { return key; }
    public String destinationName() { return destinationName; }
    public Money amount() { return amount; }
    public String message() { return message; }
    public LocalDate scheduledFor() { return scheduledFor; }
    public PixScheduleStatus status() { return status; }
    public String failureReason() { return failureReason; }
    public PixTransferId executedPixId() { return executedPixId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
