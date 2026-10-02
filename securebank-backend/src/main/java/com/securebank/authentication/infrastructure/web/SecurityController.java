package com.securebank.authentication.infrastructure.web;

import com.securebank.authentication.application.SecurityApplicationService;
import com.securebank.shared.domain.SessionId;
import com.securebank.shared.infrastructure.web.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Segurança da própria conta: sessões ativas, troca de senha e MFA. Vale para qualquer usuário autenticado. */
@RestController
@RequestMapping("/api/v1/security")
class SecurityController {

    record SessionResponse(UUID id, Instant createdAt, Instant lastUsedAt, Instant expiresAt, String ip,
            String userAgent, boolean mfaVerified, boolean current) {}

    record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword) {}

    record CodeRequest(@NotBlank @Size(min = 6, max = 6) String code) {}

    record MfaSetupResponse(String secret, String otpauthUri) {}

    private final SecurityApplicationService security;
    private final CurrentUser current;

    SecurityController(SecurityApplicationService security, CurrentUser current) {
        this.security = security;
        this.current = current;
    }

    @GetMapping("/sessions")
    List<SessionResponse> sessions() {
        return security.sessions(current.userId(), current.sessionId()).stream()
                .map(v -> new SessionResponse(v.session().id().value(), v.session().createdAt(),
                        v.session().lastUsedAt(), v.session().expiresAt(), v.session().ip(),
                        v.session().userAgent(), v.session().mfaVerified(), v.current()))
                .toList();
    }

    @DeleteMapping("/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeSession(@PathVariable String id) {
        security.revokeSession(current.userId(), SessionId.of(id));
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        security.changePassword(current.userId(), current.sessionId(), request.currentPassword(),
                request.newPassword());
    }

    record MfaStatusResponse(boolean enabled) {}

    @GetMapping("/mfa")
    MfaStatusResponse mfaStatus() {
        return new MfaStatusResponse(security.mfaEnabled(current.userId()));
    }

    /** Passo 1: devolve o segredo (uma única vez) para cadastrar no app autenticador. O MFA só liga no /confirm. */
    @PostMapping("/mfa")
    @ResponseStatus(HttpStatus.CREATED)
    MfaSetupResponse setupMfa() {
        var setup = security.setupMfa(current.userId());
        return new MfaSetupResponse(setup.secret(), setup.otpauthUri());
    }

    /** Passo 2: o primeiro código válido liga o MFA. */
    @PostMapping("/mfa/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmMfa(@Valid @RequestBody CodeRequest request) {
        security.confirmMfa(current.userId(), request.code());
    }

    /** Desligar exige um código válido: um token roubado sozinho não derruba a proteção. */
    @DeleteMapping("/mfa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void disableMfa(@Valid @RequestBody CodeRequest request) {
        security.disableMfa(current.userId(), request.code());
    }
}
