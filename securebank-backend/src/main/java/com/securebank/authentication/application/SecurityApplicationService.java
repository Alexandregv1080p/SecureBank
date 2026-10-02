package com.securebank.authentication.application;

import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.authentication.domain.MfaDevice;
import com.securebank.authentication.domain.PasswordPolicy;
import com.securebank.authentication.domain.Session;
import com.securebank.authentication.domain.SessionRevocation;
import com.securebank.authentication.domain.Totp;
import com.securebank.authentication.domain.User;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.RateLimiter;
import com.securebank.shared.domain.SessionId;
import com.securebank.shared.domain.UserId;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão da própria conta de acesso: sessões, senha e MFA. Sempre sobre o usuário autenticado. */
@Service
@Transactional(noRollbackFor = ApplicationException.class)
public class SecurityApplicationService {

    private static final String MFA_ISSUER = "SecureBank";

    public record SessionView(Session session, boolean current) {}

    /** Dados para cadastrar o segredo no app autenticador — mostrados uma única vez. */
    public record MfaSetup(String secret, String otpauthUri) {}

    private final UserRepository users;
    private final SessionRepository sessions;
    private final MfaDeviceRepository mfaDevices;
    private final PasswordHasher hasher;
    private final SecretCipher cipher;
    private final RateLimiter limiter;
    private final SessionRevoker revoker;
    private final AuditService audit;
    private final BankTime time;
    private final AuthSettings settings;

    public SecurityApplicationService(UserRepository users, SessionRepository sessions,
            MfaDeviceRepository mfaDevices, PasswordHasher hasher, SecretCipher cipher, RateLimiter limiter,
            SessionRevoker revoker, AuditService audit, BankTime time, AuthSettings settings) {
        this.users = users;
        this.sessions = sessions;
        this.mfaDevices = mfaDevices;
        this.hasher = hasher;
        this.cipher = cipher;
        this.limiter = limiter;
        this.revoker = revoker;
        this.audit = audit;
        this.time = time;
        this.settings = settings;
    }

    // ---------- sessões ----------

    @Transactional(readOnly = true)
    public List<SessionView> sessions(UserId userId, SessionId current) {
        return sessions.findActiveByUser(userId, time.now()).stream()
                .map(s -> new SessionView(s, s.id().equals(current))).toList();
    }

    /** Sessão de outro usuário é indistinguível de inexistente (404). */
    public void revokeSession(UserId userId, SessionId target) {
        Session session = sessions.findById(target)
                .filter(s -> s.userId().equals(userId) && s.isActive(time.now()))
                .orElseThrow(() -> ApplicationException.notFound("Session"));
        revoker.revoke(session, SessionRevocation.USER_REVOKED);
        audit.record(AuditEntry.of(AuditEvent.SESSION_REVOKED).detail(target.toString()));
    }

    public void logout(UserId userId, SessionId current) {
        sessions.findById(current).filter(s -> s.userId().equals(userId)).ifPresent(session -> {
            revoker.revoke(session, SessionRevocation.LOGOUT);
            audit.record(AuditEntry.of(AuditEvent.LOGOUT));
        });
    }

    // ---------- senha ----------

    public void changePassword(UserId userId, SessionId current, String currentPassword, String newPassword) {
        User user = users.findById(userId).orElseThrow(ApplicationException::unauthenticated);
        String failKey = "pwd:fail:" + userId;
        if (limiter.count(failKey) >= settings.loginMaxFailures()) {
            throw ApplicationException.tooManyRequests("TOO_MANY_ATTEMPTS", "Too many failed attempts. Try again later",
                    settings.loginFailureWindow());
        }
        if (!hasher.matches(currentPassword, user.passwordHash())) {
            limiter.hit(failKey, settings.loginMaxFailures(), settings.loginFailureWindow());
            throw ApplicationException.unprocessable("INVALID_CURRENT_PASSWORD", "Current password is incorrect");
        }
        PasswordPolicy.validate(newPassword, user.email());
        limiter.reset(failKey);

        user.changePassword(hasher.hash(newPassword), time.now());
        users.save(user);
        // Quem tinha a senha antiga (ou um token roubado) perde acesso; só a sessão atual continua.
        Session keep = sessions.findById(current).orElse(null);
        revoker.revokeAll(userId, SessionRevocation.PASSWORD_CHANGED, keep);
        audit.record(AuditEntry.of(AuditEvent.PASSWORD_CHANGED));
    }

    // ---------- MFA ----------

    @Transactional(readOnly = true)
    public boolean mfaEnabled(UserId userId) {
        return mfaDevices.findByUser(userId).filter(MfaDevice::isActive).isPresent();
    }

    public MfaSetup setupMfa(UserId userId) {
        User user = users.findById(userId).orElseThrow(ApplicationException::unauthenticated);
        if (mfaDevices.findByUser(userId).filter(MfaDevice::isActive).isPresent()) {
            throw ApplicationException.conflict("MFA_ALREADY_ENABLED", "MFA is already enabled");
        }
        String secret = Totp.generateSecret();
        mfaDevices.deleteByUser(userId); // recomeçar um cadastro pendente
        mfaDevices.save(MfaDevice.pending(userId, cipher.encrypt(secret), time.now()));
        return new MfaSetup(secret, Totp.otpauthUri(MFA_ISSUER, user.email().value(), secret));
    }

    public void confirmMfa(UserId userId, String code) {
        MfaDevice device = mfaDevices.findByUser(userId).filter(d -> !d.isActive())
                .orElseThrow(() -> ApplicationException.conflict("MFA_NOT_PENDING", "No MFA setup in progress"));
        long step = requireValidCode(userId, device, code, "confirm");
        device.activate(step, time.now());
        mfaDevices.save(device);
        audit.record(AuditEntry.of(AuditEvent.MFA_ENABLED));
    }

    public void disableMfa(UserId userId, String code) {
        MfaDevice device = mfaDevices.findByUser(userId).filter(MfaDevice::isActive)
                .orElseThrow(() -> ApplicationException.conflict("MFA_NOT_ENABLED", "MFA is not enabled"));
        requireValidCode(userId, device, code, "disable");
        mfaDevices.deleteByUser(userId);
        audit.record(AuditEntry.of(AuditEvent.MFA_DISABLED));
    }

    private long requireValidCode(UserId userId, MfaDevice device, String code, String purpose) {
        String failKey = "mfa:fail:" + userId;
        if (limiter.count(failKey) >= settings.mfaMaxFailures()) {
            throw ApplicationException.tooManyRequests("TOO_MANY_ATTEMPTS", "Too many failed attempts. Try again later",
                    settings.mfaFailureWindow());
        }
        OptionalLong step = Totp.verify(cipher.decrypt(device.secretCipher()), code, time.now(), device.lastUsedStep());
        if (step.isEmpty()) {
            limiter.hit(failKey, settings.mfaMaxFailures(), settings.mfaFailureWindow());
            audit.record(AuditEntry.of(AuditEvent.MFA_FAILED).detail(purpose));
            throw ApplicationException.unprocessable("INVALID_MFA_CODE", "Invalid code");
        }
        limiter.reset(failKey);
        return step.getAsLong();
    }
}
