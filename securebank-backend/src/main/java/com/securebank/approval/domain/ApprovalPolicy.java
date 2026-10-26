package com.securebank.approval.domain;

import com.securebank.authorization.domain.Department;
import com.securebank.authorization.domain.Role;
import com.securebank.authorization.domain.StaffLevel;
import java.math.BigDecimal;

/**
 * Alçadas: o que cada posição resolve sozinha e quem pode decidir o que sobrou. Função pura, para testar sem banco.
 * <ul>
 *   <li>Limite de cartão: o analista de Crédito define até R$ 20.000,00 (e sempre pode reduzir); acima disso, um gerente
 *       de Crédito decide. Gerente e Diretoria resolvem direto (o teto de R$ 100.000,00 vale para todos).</li>
 *   <li>Desbloqueio de conta: bloquear é urgente e imediato, mas desbloquear exige gerente de Risco ou a Diretoria.</li>
 * </ul>
 */
public final class ApprovalPolicy {

    public static final BigDecimal ANALYST_CREDIT_LIMIT = new BigDecimal("20000.00");

    private ApprovalPolicy() {}

    /** Esta pessoa resolve a ação agora, sem pedir a ninguém? {@code newLimit} e {@code currentLimit} só valem para limite. */
    public static boolean canActDirectly(ApprovalKind kind, Role role, Department department, StaffLevel level,
            BigDecimal newLimit, BigDecimal currentLimit) {
        if (role == Role.ADMIN || isManagerOf(kind, department, level)) {
            return true;
        }
        if (department != kind.department()) {
            return false; // a rota já barra, mas ninguém resolve nada fora do departamento
        }
        return switch (kind) {
            case CREDIT_LIMIT -> newLimit.compareTo(ANALYST_CREDIT_LIMIT) <= 0 || newLimit.compareTo(currentLimit) <= 0;
            case ACCOUNT_UNBLOCK -> false;
        };
    }

    /** Pode aprovar ou recusar pedidos deste tipo: Diretoria, ou gerente do departamento dono do tipo. */
    public static boolean canDecide(ApprovalKind kind, Role role, Department department, StaffLevel level) {
        return role == Role.ADMIN || isManagerOf(kind, department, level);
    }

    private static boolean isManagerOf(ApprovalKind kind, Department department, StaffLevel level) {
        return department == kind.department() && level == StaffLevel.MANAGER;
    }
}
