package com.securebank.authentication.application;

import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.authentication.domain.PasswordPolicy;
import com.securebank.authentication.domain.SessionRevocation;
import com.securebank.authentication.domain.User;
import com.securebank.authorization.domain.Department;
import com.securebank.authorization.domain.StaffLevel;
import com.securebank.shared.application.ActorContext;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gestão de usuários da equipe: departamento, nível e acesso (permissão MANAGE_USERS). */
@Service
@Transactional
public class UserAdminApplicationService {

    /** Pessoa da equipe e se ela já ligou a verificação em duas etapas. */
    public record StaffMember(User user, boolean mfaEnabled) {}

    private final UserRepository users;
    private final MfaDeviceRepository mfaDevices;
    private final PasswordHasher hasher;
    private final SessionRevoker revoker;
    private final AuditService audit;
    private final ActorContext actors;
    private final BankTime time;

    public UserAdminApplicationService(UserRepository users, MfaDeviceRepository mfaDevices, PasswordHasher hasher,
            SessionRevoker revoker, AuditService audit, ActorContext actors, BankTime time) {
        this.users = users;
        this.mfaDevices = mfaDevices;
        this.hasher = hasher;
        this.revoker = revoker;
        this.audit = audit;
        this.actors = actors;
        this.time = time;
    }

    public User createStaff(String email, String password, Department department, StaffLevel level) {
        Email mail = Email.forInput(email);
        PasswordPolicy.validate(password, mail);
        if (users.existsByEmail(mail)) {
            throw ApplicationException.conflict("USER_ALREADY_EXISTS", "User already exists");
        }
        User user = User.staff(mail, hasher.hash(password), department, level, time.now());
        users.save(user);
        audit.record(AuditEntry.of(AuditEvent.USER_CREATED).detail(user.id() + " " + department + "/" + level));
        return user;
    }

    @Transactional(readOnly = true)
    public java.util.List<StaffMember> listStaff() {
        return users.findStaff().stream()
                .map(u -> new StaffMember(u, mfaDevices.findByUser(u.id()).filter(d -> d.isActive()).isPresent()))
                .toList();
    }

    /**
     * Muda a pessoa de departamento e/ou nível. O papel acompanha o departamento, então as sessões abertas caem: o
     * token antigo ainda carregaria as permissões de antes. Ninguém muda a própria posição.
     */
    public User assign(UserId target, Department department, StaffLevel level) {
        if (target.equals(actors.current().userId())) {
            throw ApplicationException.conflict("CANNOT_CHANGE_SELF", "You cannot change your own position");
        }
        User user = find(target);
        if (user.department() == department && user.level() == level) {
            return user;
        }
        String before = user.department() + "/" + user.level();
        user.assign(department, level, time.now());
        users.save(user);
        revoker.revokeAll(target, SessionRevocation.ROLE_CHANGED, null);
        audit.record(AuditEntry.of(AuditEvent.USER_ASSIGNED).detail(target + " " + before + " -> " + department + "/" + level));
        return user;
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
        users.save(User.staff(mail, hasher.hash(password), Department.DIRETORIA, StaffLevel.MANAGER, time.now()));
        return true;
    }

    private User find(UserId id) {
        return users.findById(id).orElseThrow(() -> ApplicationException.notFound("User"));
    }
}
