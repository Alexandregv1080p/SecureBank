package com.securebank.authentication.application;

import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.authentication.domain.PasswordPolicy;
import com.securebank.authentication.domain.SessionRevocation;
import com.securebank.authentication.domain.User;
import com.securebank.authorization.domain.Role;
import com.securebank.shared.application.ActorContext;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de usuários da equipe (permissão MANAGE_USERS). */
@Service
@Transactional
public class UserAdminApplicationService {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final SessionRevoker revoker;
    private final AuditService audit;
    private final ActorContext actors;
    private final BankTime time;

    public UserAdminApplicationService(UserRepository users, PasswordHasher hasher, SessionRevoker revoker,
            AuditService audit, ActorContext actors, BankTime time) {
        this.users = users;
        this.hasher = hasher;
        this.revoker = revoker;
        this.audit = audit;
        this.actors = actors;
        this.time = time;
    }

    public User createStaff(String email, String password, Role role) {
        Email mail = new Email(email);
        PasswordPolicy.validate(password, mail);
        if (users.existsByEmail(mail)) {
            throw ApplicationException.conflict("USER_ALREADY_EXISTS", "User already exists");
        }
        User user = User.staff(mail, hasher.hash(password), role, time.now());
        users.save(user);
        audit.record(AuditEntry.of(AuditEvent.USER_CREATED).detail(user.id() + " " + role));
        return user;
    }

    @Transactional(readOnly = true)
    public java.util.List<User> listStaff() {
        return users.findStaff();
    }

    public void disable(UserId target) {
        if (target.equals(actors.current().userId())) {
            throw ApplicationException.conflict("CANNOT_DISABLE_SELF", "You cannot disable your own user");
        }
        User user = find(target);
        user.disable(time.now());
        users.save(user);
        revoker.revokeAll(target, SessionRevocation.USER_DISABLED, null);
        audit.record(AuditEntry.of(AuditEvent.USER_DISABLED).detail(target.toString()));
    }

    public void enable(UserId target) {
        User user = find(target);
        user.enable(time.now());
        users.save(user);
        audit.record(AuditEntry.of(AuditEvent.USER_ENABLED).detail(target.toString()));
    }

    /** Primeiro ADMIN do ambiente, a partir de configuração; não faz nada se o e-mail já existe. */
    public boolean bootstrapAdmin(String email, String password) {
        Email mail = new Email(email);
        if (users.existsByEmail(mail)) {
            return false;
        }
        PasswordPolicy.validate(password, mail);
        users.save(User.staff(mail, hasher.hash(password), Role.ADMIN, time.now()));
        return true;
    }

    private User find(UserId id) {
        return users.findById(id).orElseThrow(() -> ApplicationException.notFound("User"));
    }
}
