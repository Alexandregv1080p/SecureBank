package com.securebank.authentication.application;

import com.securebank.authentication.domain.Session;
import com.securebank.shared.domain.SessionId;
import com.securebank.shared.domain.UserId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionRepository {

    Optional<Session> findById(SessionId id);

    /** Sessões não revogadas e não expiradas do usuário, mais recentes primeiro. */
    List<Session> findActiveByUser(UserId userId, Instant now);

    void save(Session session);
}
