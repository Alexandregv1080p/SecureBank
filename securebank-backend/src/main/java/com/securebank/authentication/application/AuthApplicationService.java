package com.securebank.authentication.application;

import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.audit.domain.IpMasker;
import com.securebank.authentication.domain.Digests;
import com.securebank.authentication.domain.MfaDevice;
import com.securebank.authentication.domain.PasswordPolicy;
import com.securebank.authentication.domain.RefreshToken;
import com.securebank.authentication.domain.Session;
import com.securebank.authentication.domain.SessionRevocation;
import com.securebank.authentication.domain.Totp;
import com.securebank.authentication.domain.User;
import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.customer.domain.Customer;
import com.securebank.outbox.application.OutboxService;
import com.securebank.shared.application.Actor;
import com.securebank.shared.application.ActorContext;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.RateLimiter;
import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro, login (com MFA), renovação e encerramento de sessão. Os métodos que terminam em 401/429 usam
 * {@code noRollbackFor}: a auditoria, o contador de falhas e a revogação gravados antes do erro precisam persistir.
 */
@Service
public class AuthApplicationService {

    private static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";

    private final UserRepository users;
    private final SessionRepository sessions;
    private final RefreshTokenRepository refreshTokens;
    private final MfaDeviceRepository mfaDevices;
    private final PasswordHasher hasher;
    private final TokenIssuer tokens;
    private final SecretCipher cipher;
    private final RateLimiter limiter;
    private final CustomerApplicationService customers;
    private final SessionRevoker revoker;
    private final AuditService audit;
    private final OutboxService outbox;
    private final ActorContext actors;
    private final BankTime time;
    private final AuthSettings settings;
    /** Hash descartável: login de e-mail inexistente gasta o mesmo tempo de um existente (anti-enumeração por timing). */
    private final String dummyHash;

    public AuthApplicationService(UserRepository users, SessionRepository sessions,
            RefreshTokenRepository refreshTokens, MfaDeviceRepository mfaDevices, PasswordHasher hasher,
            TokenIssuer tokens, SecretCipher cipher, RateLimiter limiter, CustomerApplicationService customers,
            SessionRevoker revoker, AuditService audit, OutboxService outbox, ActorContext actors, BankTime time, AuthSettings settings) {
        this.users = users;
        this.sessions = sessions;
        this.refreshTokens = refreshTokens;
        this.mfaDevices = mfaDevices;
        this.hasher = hasher;
        this.tokens = tokens;
        this.cipher = cipher;
        this.limiter = limiter;
        this.customers = customers;
        this.revoker = revoker;
        this.audit = audit;
        this.outbox = outbox;
        this.actors = actors;
        this.time = time;
        this.settings = settings;
        this.dummyHash = hasher.hash("dummy-password-for-timing-equalization");
    }

    @Transactional
    public User register(String name, String document, String email, String phone, String password) {
        Email mail = new Email(email);
        PasswordPolicy.validate(password, mail);
        if (users.existsByEmail(mail)) {
            throw ApplicationException.conflict("CUSTOMER_ALREADY_EXISTS", "Customer already registered");
        }
        Customer customer = customers.register(name, document, email, phone);
        User user = User.forCustomer(mail, hasher.hash(password), customer.id(), time.now());
        users.save(user);
        audit.record(AuditEntry.of(AuditEvent.USER_REGISTERED).user(user.id()));
        return user;
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public LoginResult login(String email, String password) {
        Email mail = new Email(email);
        String failKey = "login:fail:" + Digests.sha256Url(mail.value());

        if (limiter.count(failKey) >= settings.loginMaxFailures()) {
            AuditEntry locked = users.findByEmail(mail).map(u -> AuditEntry.of(AuditEvent.LOGIN_FAILED).user(u.id()))
                    .orElseGet(() -> AuditEntry.of(AuditEvent.LOGIN_FAILED));
            audit.record(locked.detail("locked"));
            throw ApplicationException.tooManyRequests("TOO_MANY_ATTEMPTS",
                    "Too many failed attempts. Try again later", settings.loginFailureWindow());
        }

        Optional<User> found = users.findByEmail(mail);
        boolean passwordOk = false;
        if (found.isPresent()) {
            passwordOk = hasher.matches(password, found.get().passwordHash());
        } else {
            hasher.matches(password, dummyHash); // só para gastar o mesmo tempo; o resultado não importa
        }
        if (found.isEmpty() || !passwordOk || !found.get().isActive()) {
            limiter.hit(failKey, settings.loginMaxFailures(), settings.loginFailureWindow());
            // O motivo real (e-mail inexistente, senha errada, conta desativada) fica só na auditoria.
            String reason = found.isEmpty() ? "unknown user" : !passwordOk ? "bad password" : "user disabled";
            audit.record(found.map(u -> AuditEntry.of(AuditEvent.LOGIN_FAILED).user(u.id()))
                    .orElseGet(() -> AuditEntry.of(AuditEvent.LOGIN_FAILED)).detail(reason));
            throw ApplicationException.unauthenticated(INVALID_CREDENTIALS, "Invalid credentials");
        }

        User user = found.get();
        limiter.reset(failKey);
        Optional<MfaDevice> device = mfaDevices.findByUser(user.id());
        if (device.isPresent() && device.get().isActive()) {
            return new LoginResult.MfaRequired(tokens.issueMfaChallenge(user.id()),
                    settings.mfaChallengeTtl().toSeconds());
        }
        TokenPair pair = startSession(user, false);
        audit.record(AuditEntry.of(AuditEvent.LOGIN_SUCCESS).user(user.id()));
        loggedIn(user, false);
        return new LoginResult.Authenticated(pair);
    }

    /** 2ª etapa do login: troca o token de desafio + código TOTP por tokens de sessão. */
    @Transactional(noRollbackFor = ApplicationException.class)
    public TokenPair verifyMfa(String mfaToken, String code) {
        UserId userId = tokens.parseMfaChallenge(mfaToken);
        String failKey = "mfa:fail:" + userId;
        if (limiter.count(failKey) >= settings.mfaMaxFailures()) {
            audit.record(AuditEntry.of(AuditEvent.MFA_FAILED).user(userId).detail("locked"));
            throw ApplicationException.tooManyRequests("TOO_MANY_ATTEMPTS",
                    "Too many failed attempts. Try again later", settings.mfaFailureWindow());
        }

        User user = users.findById(userId).filter(User::isActive)
                .orElseThrow(() -> ApplicationException.unauthenticated("INVALID_MFA_CODE", "Invalid code"));
        MfaDevice device = mfaDevices.findByUser(userId).filter(MfaDevice::isActive)
                .orElseThrow(() -> ApplicationException.unauthenticated("INVALID_MFA_CODE", "Invalid code"));

        OptionalLong step = Totp.verify(cipher.decrypt(device.secretCipher()), code, time.now(), device.lastUsedStep());
        if (step.isEmpty()) {
            limiter.hit(failKey, settings.mfaMaxFailures(), settings.mfaFailureWindow());
            audit.record(AuditEntry.of(AuditEvent.MFA_FAILED).user(userId).detail("login"));
            throw ApplicationException.unauthenticated("INVALID_MFA_CODE", "Invalid code");
        }
        device.recordUse(step.getAsLong());
        mfaDevices.save(device);
        limiter.reset(failKey);

        TokenPair pair = startSession(user, true);
        audit.record(AuditEntry.of(AuditEvent.LOGIN_SUCCESS).user(userId).detail("mfa"));
        loggedIn(user, true);
        return pair;
    }

    /**
     * Rotação: cada refresh token vale uma vez. Reapresentar um já usado indica token roubado (ou cliente duplicado):
     * a sessão inteira é revogada, derrubando também quem ficou com o token novo.
     */
    @Transactional(noRollbackFor = ApplicationException.class)
    public TokenPair refresh(String rawToken) {
        Instant now = time.now();
        RefreshToken token = refreshTokens.findByHash(RefreshToken.hash(rawToken)).orElseThrow(AuthApplicationService::invalidRefresh);
        Session session = sessions.findById(token.sessionId()).orElseThrow(AuthApplicationService::invalidRefresh);
        if (!session.isActive(now) || token.isExpired(now)) {
            throw invalidRefresh();
        }
        if (!refreshTokens.markUsed(token.id(), now)) {
            revoker.revoke(session, SessionRevocation.REUSE_DETECTED);
            audit.record(AuditEntry.of(AuditEvent.REFRESH_TOKEN_REUSE_DETECTED).user(session.userId()));
            throw invalidRefresh();
        }
        User user = users.findById(session.userId()).filter(User::isActive).orElse(null);
        if (user == null) {
            revoker.revoke(session, SessionRevocation.USER_DISABLED);
            throw invalidRefresh();
        }

        session.touch(now);
        sessions.save(session);
        RefreshToken.Issued next = RefreshToken.issue(session.id(), now, refreshExpiry(session, now));
        refreshTokens.save(next.record());
        TokenIssuer.AccessToken access = tokens.issueAccessToken(user, session);
        return new TokenPair(access.value(), access.expiresInSeconds(), next.rawValue());
    }

    private void loggedIn(User user, boolean mfa) {
        outbox.record("UserLoggedIn", "User", user.id().toString(),
                java.util.Map.of("userId", user.id().toString(), "mfa", mfa));
    }

    private TokenPair startSession(User user, boolean mfaVerified) {
        Instant now = time.now();
        Actor actor = actors.current();
        Session session = Session.start(user.id(), mfaVerified, IpMasker.mask(actor.ip()), actor.userAgent(),
                settings.sessionMaxLifetime(), now);
        sessions.save(session);
        RefreshToken.Issued refresh = RefreshToken.issue(session.id(), now, refreshExpiry(session, now));
        refreshTokens.save(refresh.record());
        TokenIssuer.AccessToken access = tokens.issueAccessToken(user, session);
        return new TokenPair(access.value(), access.expiresInSeconds(), refresh.rawValue());
    }

    /** O refresh token nunca vive além da vida máxima da sessão. */
    private Instant refreshExpiry(Session session, Instant now) {
        Instant byTtl = now.plus(settings.refreshTokenTtl());
        return byTtl.isAfter(session.expiresAt()) ? session.expiresAt() : byTtl;
    }

    private static ApplicationException invalidRefresh() {
        return ApplicationException.unauthenticated("INVALID_REFRESH_TOKEN", "Invalid refresh token");
    }
}
