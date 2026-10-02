package com.securebank.authentication.application;

import com.securebank.authentication.domain.Session;
import com.securebank.authentication.domain.SessionRevocation;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.domain.UserId;
import java.util.List;
import org.springframework.stereotype.Component;

/** Revoga sessão no banco (barra novos refresh) e na denylist (barra os access tokens já emitidos). */
@Component
public class SessionRevoker {

    private final SessionRepository sessions;
    private final SessionDenylist denylist;
    private final BankTime time;

    public SessionRevoker(SessionRepository sessions, SessionDenylist denylist, BankTime time) {
        this.sessions = sessions;
        this.denylist = denylist;
        this.time = time;
    }

    public void revoke(Session session, SessionRevocation reason) {
        session.revoke(reason, time.now());
        sessions.save(session);
        denylist.revoke(session.id());
    }

    /** @param keep sessão que continua valendo (ex.: a que acabou de trocar a senha), ou null para revogar todas */
    public void revokeAll(UserId userId, SessionRevocation reason, Session keep) {
        List<Session> active = sessions.findActiveByUser(userId, time.now());
        for (Session session : active) {
            if (keep == null || !session.id().equals(keep.id())) {
                revoke(session, reason);
            }
        }
    }
}
