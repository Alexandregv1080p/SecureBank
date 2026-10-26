package com.securebank.approval.domain;

import com.securebank.authorization.domain.Department;

/** O que pode ficar esperando um gerente. Cada tipo pertence a um departamento, que é quem decide. */
public enum ApprovalKind {
    /** Aumento de limite de cartão acima da alçada do analista de Crédito. */
    CREDIT_LIMIT(Department.CREDITO),
    /** Desbloqueio de conta pedido por analista de Risco (bloquear é imediato; desbloquear passa por um gerente). */
    ACCOUNT_UNBLOCK(Department.RISCO);

    private final Department department;

    ApprovalKind(Department department) {
        this.department = department;
    }

    public Department department() {
        return department;
    }
}
