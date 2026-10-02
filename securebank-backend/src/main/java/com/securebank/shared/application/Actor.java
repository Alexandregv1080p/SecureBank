package com.securebank.shared.application;

import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.SessionId;
import com.securebank.shared.domain.UserId;

/** Quem está fazendo a requisição corrente (campos nulos quando anônimo ou fora de uma requisição HTTP). */
public record Actor(UserId userId, CustomerId customerId, SessionId sessionId, String ip, String userAgent,
        String traceId) {

    public static Actor none() {
        return new Actor(null, null, null, null, null, null);
    }
}
