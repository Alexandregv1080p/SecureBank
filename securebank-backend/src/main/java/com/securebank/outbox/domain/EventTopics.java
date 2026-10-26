package com.securebank.outbox.domain;

import java.util.List;

/** Um tópico por família de evento; a chave da mensagem é o aggregateId (mesmo agregado = mesma partição = ordem). */
public final class EventTopics {

    public static final String TRANSFERS = "securebank.transfers";
    public static final String PAYMENTS = "securebank.payments";
    public static final String ACCOUNTS = "securebank.accounts";
    public static final String USERS = "securebank.users";
    public static final String CARDS = "securebank.cards";
    public static final String DLT_SUFFIX = ".DLT";

    public static final List<String> ALL = List.of(TRANSFERS, PAYMENTS, ACCOUNTS, USERS, CARDS);

    private EventTopics() {}

    public static String forEvent(String eventType) {
        if (eventType.startsWith("Transfer") || eventType.startsWith("Pix")) {
            return TRANSFERS;
        }
        if (eventType.startsWith("Payment")) {
            return PAYMENTS;
        }
        if (eventType.startsWith("Account") || eventType.startsWith("Piggy")
                || eventType.startsWith("Investment") || eventType.startsWith("Fx")) {
            return ACCOUNTS;
        }
        if (eventType.startsWith("User")) {
            return USERS;
        }
        if (eventType.startsWith("Card") || eventType.startsWith("Credit")) {
            return CARDS;
        }
        throw new IllegalArgumentException("No topic for event type " + eventType);
    }
}
