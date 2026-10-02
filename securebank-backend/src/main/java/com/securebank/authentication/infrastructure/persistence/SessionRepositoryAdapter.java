package com.securebank.authentication.infrastructure.persistence;

import com.securebank.authentication.application.SessionRepository;
import com.securebank.authentication.domain.Session;
import com.securebank.shared.domain.SessionId;
import com.securebank.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class SessionRepositoryAdapter implements SessionRepository {

    private final EntityManager em;

    SessionRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<Session> findById(SessionId id) {
        return Optional.ofNullable(em.find(SessionEntity.class, id.value())).map(SessionEntity::toDomain);
    }

    @Override
    public List<Session> findActiveByUser(UserId userId, Instant now) {
        return em.createQuery("select s from SessionEntity s where s.userId = :user and s.revokedAt is null"
                        + " and s.expiresAt > :now order by s.createdAt desc, s.id", SessionEntity.class)
                .setParameter("user", userId.value())
                .setParameter("now", now)
                .getResultList().stream().map(SessionEntity::toDomain).toList();
    }

    @Override
    public void save(Session session) {
        SessionEntity entity = em.find(SessionEntity.class, session.id().value());
        if (entity == null) {
            entity = new SessionEntity();
            entity.apply(session);
            em.persist(entity);
        } else {
            entity.apply(session);
        }
    }
}
