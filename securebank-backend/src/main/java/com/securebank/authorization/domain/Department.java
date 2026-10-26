package com.securebank.authorization.domain;

/**
 * Departamento da equipe. Cada um tem o papel (conjunto de permissões) que as pessoas dele recebem: quem está em
 * Risco é {@link Role#RISK}, e assim por diante. A Diretoria é a única com {@link Role#ADMIN}.
 */
public enum Department {
    ATENDIMENTO(Role.SUPPORT),
    RISCO(Role.RISK),
    CREDITO(Role.CREDIT),
    FINANCEIRO(Role.FINANCE),
    AUDITORIA(Role.AUDITOR),
    DIRETORIA(Role.ADMIN);

    private final Role role;

    Department(Role role) {
        this.role = role;
    }

    public Role role() {
        return role;
    }
}
