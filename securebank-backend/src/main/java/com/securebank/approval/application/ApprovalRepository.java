package com.securebank.approval.application;

import com.securebank.approval.domain.ApprovalKind;
import com.securebank.approval.domain.ApprovalRequest;
import com.securebank.approval.domain.ApprovalStatus;
import com.securebank.shared.domain.UserId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApprovalRepository {

    Optional<ApprovalRequest> findById(UUID id);

    /** Mais recentes primeiro, no máximo 100. Qualquer filtro nulo (ou lista vazia de tipos = nenhum tipo) é ignorado. */
    List<ApprovalRequest> find(ApprovalStatus status, Collection<ApprovalKind> kinds, UserId requestedBy);

    boolean existsPending(ApprovalKind kind, UUID subjectId);

    long countPending(Collection<ApprovalKind> kinds);

    void save(ApprovalRequest request);
}
