package com.securebank.approval.infrastructure.persistence;

import com.securebank.approval.application.ApprovalRepository;
import com.securebank.approval.domain.ApprovalKind;
import com.securebank.approval.domain.ApprovalRequest;
import com.securebank.approval.domain.ApprovalStatus;
import com.securebank.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
class ApprovalRepositoryAdapter implements ApprovalRepository {

    private static final int PAGE = 100;

    private final EntityManager em;

    ApprovalRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<ApprovalRequest> findById(UUID id) {
        return Optional.ofNullable(em.find(ApprovalEntity.class, id)).map(ApprovalEntity::toDomain);
    }

    /** {@code kinds} nulo = todos os tipos; vazio = nenhum. */
    @Override
    public List<ApprovalRequest> find(ApprovalStatus status, Collection<ApprovalKind> kinds, UserId requestedBy) {
        if (kinds != null && kinds.isEmpty()) {
            return List.of();
        }
        StringBuilder jpql = new StringBuilder("select a from ApprovalEntity a where 1 = 1");
        if (status != null) {
            jpql.append(" and a.status = :status");
        }
        if (kinds != null) {
            jpql.append(" and a.kind in :kinds");
        }
        if (requestedBy != null) {
            jpql.append(" and a.requestedBy = :requestedBy");
        }
        var query = em.createQuery(jpql.append(" order by a.createdAt desc, a.id").toString(), ApprovalEntity.class);
        if (status != null) {
            query.setParameter("status", status);
        }
        if (kinds != null) {
            query.setParameter("kinds", kinds);
        }
        if (requestedBy != null) {
            query.setParameter("requestedBy", requestedBy.value());
        }
        return query.setMaxResults(PAGE).getResultList().stream().map(ApprovalEntity::toDomain).toList();
    }

    @Override
    public boolean existsPending(ApprovalKind kind, UUID subjectId) {
        return em.createQuery("select count(a) from ApprovalEntity a where a.kind = :kind and a.subjectId = :subject"
                        + " and a.status = :pending", Long.class)
                .setParameter("kind", kind).setParameter("subject", subjectId).setParameter("pending", ApprovalStatus.PENDING)
                .getSingleResult() > 0;
    }

    @Override
    public long countPending(Collection<ApprovalKind> kinds) {
        if (kinds.isEmpty()) {
            return 0;
        }
        return em.createQuery("select count(a) from ApprovalEntity a where a.status = :pending and a.kind in :kinds", Long.class)
                .setParameter("pending", ApprovalStatus.PENDING).setParameter("kinds", kinds).getSingleResult();
    }

    @Override
    public void save(ApprovalRequest request) {
        ApprovalEntity entity = em.find(ApprovalEntity.class, request.id());
        if (entity == null) {
            entity = new ApprovalEntity();
            entity.apply(request);
            em.persist(entity);
        } else {
            entity.apply(request);
        }
    }
}
