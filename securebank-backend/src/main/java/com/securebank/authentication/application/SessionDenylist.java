package com.securebank.authentication.application;

import com.securebank.shared.domain.SessionId;

/**
 * Sessões revogadas cujos access tokens (stateless, até 15 min) ainda não expiraram. Sem isto, logout e revogação
 * só teriam efeito no próximo refresh. A entrada expira sozinha junto com o último token possível.
 */
public interface SessionDenylist {

    void revoke(SessionId sessionId);

    boolean isRevoked(SessionId sessionId);
}
