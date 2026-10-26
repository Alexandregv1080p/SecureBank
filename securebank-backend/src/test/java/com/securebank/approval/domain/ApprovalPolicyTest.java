package com.securebank.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.authorization.domain.Department;
import com.securebank.authorization.domain.Role;
import com.securebank.authorization.domain.StaffLevel;
import com.securebank.shared.domain.InvalidStateTransitionException;
import com.securebank.shared.domain.InvalidValueException;
import com.securebank.shared.domain.UserId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ApprovalPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");

    private static BigDecimal v(String amount) {
        return new BigDecimal(amount);
    }

    private static boolean limit(Role role, Department dept, StaffLevel level, String wanted, String current) {
        return ApprovalPolicy.canActDirectly(ApprovalKind.CREDIT_LIMIT, role, dept, level, v(wanted), v(current));
    }

    @Test
    void anAnalystOfCreditActsAloneUpToTheCeilingAndAlwaysWhenReducing() {
        assertThat(limit(Role.CREDIT, Department.CREDITO, StaffLevel.ANALYST, "20000.00", "2000.00")).isTrue();
        assertThat(limit(Role.CREDIT, Department.CREDITO, StaffLevel.ANALYST, "20000.01", "2000.00")).isFalse();
        assertThat(limit(Role.CREDIT, Department.CREDITO, StaffLevel.ANALYST, "60000.00", "80000.00")).isTrue(); // reduzir
        assertThat(limit(Role.CREDIT, Department.CREDITO, StaffLevel.ANALYST, "80000.00", "80000.00")).isTrue(); // igual não aumenta
    }

    @Test
    void managersOfTheDepartmentAndTheBoardActAloneButOtherDepartmentsNever() {
        assertThat(limit(Role.CREDIT, Department.CREDITO, StaffLevel.MANAGER, "100000.00", "2000.00")).isTrue();
        assertThat(limit(Role.ADMIN, Department.DIRETORIA, StaffLevel.MANAGER, "100000.00", "2000.00")).isTrue();
        assertThat(limit(Role.RISK, Department.RISCO, StaffLevel.MANAGER, "3000.00", "2000.00")).isFalse(); // gerente de outro departamento
        assertThat(limit(Role.RISK, Department.RISCO, StaffLevel.ANALYST, "3000.00", "2000.00")).isFalse();
    }

    @Test
    void unblockingNeedsARiskManagerOrTheBoard() {
        assertThat(ApprovalPolicy.canActDirectly(ApprovalKind.ACCOUNT_UNBLOCK, Role.RISK, Department.RISCO, StaffLevel.ANALYST, null, null)).isFalse();
        assertThat(ApprovalPolicy.canActDirectly(ApprovalKind.ACCOUNT_UNBLOCK, Role.RISK, Department.RISCO, StaffLevel.MANAGER, null, null)).isTrue();
        assertThat(ApprovalPolicy.canActDirectly(ApprovalKind.ACCOUNT_UNBLOCK, Role.ADMIN, Department.DIRETORIA, StaffLevel.MANAGER, null, null)).isTrue();
        assertThat(ApprovalPolicy.canActDirectly(ApprovalKind.ACCOUNT_UNBLOCK, Role.CREDIT, Department.CREDITO, StaffLevel.MANAGER, null, null)).isFalse();
    }

    @Test
    void onlyTheOwningManagerOrTheBoardDecides() {
        assertThat(ApprovalPolicy.canDecide(ApprovalKind.CREDIT_LIMIT, Role.CREDIT, Department.CREDITO, StaffLevel.MANAGER)).isTrue();
        assertThat(ApprovalPolicy.canDecide(ApprovalKind.CREDIT_LIMIT, Role.CREDIT, Department.CREDITO, StaffLevel.ANALYST)).isFalse();
        assertThat(ApprovalPolicy.canDecide(ApprovalKind.CREDIT_LIMIT, Role.RISK, Department.RISCO, StaffLevel.MANAGER)).isFalse();
        assertThat(ApprovalPolicy.canDecide(ApprovalKind.ACCOUNT_UNBLOCK, Role.RISK, Department.RISCO, StaffLevel.MANAGER)).isTrue();
        assertThat(ApprovalPolicy.canDecide(ApprovalKind.ACCOUNT_UNBLOCK, Role.ADMIN, Department.DIRETORIA, StaffLevel.MANAGER)).isTrue();
    }

    @Test
    void aRequestIsDecidedOnceAndRejectingNeedsAReason() {
        UserId asker = UserId.newId();
        UserId manager = UserId.newId();
        ApprovalRequest request = ApprovalRequest.open(ApprovalKind.CREDIT_LIMIT, UUID.randomUUID(), v("25000.00"), "  renda comprovada ", asker, NOW);
        assertThat(request.isPending()).isTrue();
        assertThat(request.note()).isEqualTo("renda comprovada");

        assertThatThrownBy(() -> request.reject(manager, " ", NOW)).isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> request.reject(manager, "ab", NOW)).isInstanceOf(InvalidValueException.class);
        request.reject(manager, "Sem renda", NOW.plusSeconds(5));
        assertThat(request.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(request.decidedBy()).isEqualTo(manager);
        assertThatThrownBy(() -> request.approve(manager, null, NOW)).isInstanceOf(InvalidStateTransitionException.class);
        assertThatThrownBy(() -> request.cancel(asker, NOW)).isInstanceOf(InvalidStateTransitionException.class);
    }

    @Test
    void theShapeOfARequestIsChecked() {
        UserId asker = UserId.newId();
        assertThatThrownBy(() -> ApprovalRequest.open(ApprovalKind.CREDIT_LIMIT, UUID.randomUUID(), null, null, asker, NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> ApprovalRequest.open(ApprovalKind.ACCOUNT_UNBLOCK, UUID.randomUUID(), v("1.00"), null, asker, NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> ApprovalRequest.open(ApprovalKind.ACCOUNT_UNBLOCK, UUID.randomUUID(), null, "x".repeat(141), asker, NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThatThrownBy(() -> ApprovalRequest.open(ApprovalKind.ACCOUNT_UNBLOCK, UUID.randomUUID(), null, "duas\nlinhas", asker, NOW))
                .isInstanceOf(InvalidValueException.class);
        assertThat(ApprovalRequest.open(ApprovalKind.ACCOUNT_UNBLOCK, UUID.randomUUID(), null, "   ", asker, NOW).note()).isNull();
    }
}
