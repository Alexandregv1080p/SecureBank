package com.securebank.approval.application;

import com.securebank.account.application.AccountApplicationService;
import com.securebank.account.domain.Account;
import com.securebank.account.domain.AccountStatus;
import com.securebank.approval.domain.ApprovalKind;
import com.securebank.approval.domain.ApprovalPolicy;
import com.securebank.approval.domain.ApprovalRequest;
import com.securebank.approval.domain.ApprovalStatus;
import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.authentication.application.UserRepository;
import com.securebank.authentication.domain.User;
import com.securebank.authorization.domain.Department;
import com.securebank.authorization.domain.Role;
import com.securebank.credit.application.CreditApplicationService;
import com.securebank.shared.application.ActorContext;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.Money;
import com.securebank.shared.domain.UserId;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ações da equipe que passam da alçada de quem pede esperam um gerente (veja {@link ApprovalPolicy}). Quem pode agir
 * sozinho age na hora; quem não pode abre um pedido, que um gerente do departamento (ou a Diretoria) aprova, recusando
 * com motivo se for o caso. Ninguém decide o próprio pedido. Aprovar executa a ação com a pessoa que aprovou como autora.
 */
@Service
@Transactional
public class ApprovalService {

    /** Pedido pronto para a tela: com os e-mails, a descrição do alvo e o que a pessoa que consulta pode fazer com ele. */
    public record Row(ApprovalRequest request, String requesterEmail, String deciderEmail, String subject, UUID customerId,
            boolean canDecide, boolean canCancel) {}

    /** Ou a ação foi feita na hora ({@code applied}) ou virou um pedido ({@code pending}). */
    public record LimitOutcome(Money applied, Row pending) {}

    public record UnblockOutcome(Account account, Row pending) {}

    private final ApprovalRepository approvals;
    private final UserRepository users;
    private final CreditApplicationService credit;
    private final AccountApplicationService accounts;
    private final AuditService audit;
    private final ActorContext actors;
    private final BankTime time;

    public ApprovalService(ApprovalRepository approvals, UserRepository users, CreditApplicationService credit,
            AccountApplicationService accounts, AuditService audit, ActorContext actors, BankTime time) {
        this.approvals = approvals;
        this.users = users;
        this.credit = credit;
        this.accounts = accounts;
        this.audit = audit;
        this.actors = actors;
        this.time = time;
    }

    public LimitOutcome changeCreditLimit(CardId card, BigDecimal newLimit, String note) {
        User me = actor();
        CreditApplicationService.StaffView view = credit.staffViewOf(card);
        credit.checkStaffLimit(card, newLimit); // pedido inválido nem chega a um gerente
        if (ApprovalPolicy.canActDirectly(ApprovalKind.CREDIT_LIMIT, me.role(), me.department(), me.level(), newLimit,
                view.terms().limit().amount())) {
            return new LimitOutcome(credit.changeLimitAsStaff(card, newLimit), null);
        }
        return new LimitOutcome(null, row(open(ApprovalKind.CREDIT_LIMIT, card.value(), newLimit, note, me), me));
    }

    public UnblockOutcome unblockAccount(AccountId id, String note) {
        User me = actor();
        Account account = accounts.getAny(id);
        if (account.status() != AccountStatus.BLOCKED) {
            throw ApplicationException.conflict("ACCOUNT_NOT_BLOCKED", "The account is not blocked");
        }
        if (ApprovalPolicy.canActDirectly(ApprovalKind.ACCOUNT_UNBLOCK, me.role(), me.department(), me.level(), null, null)) {
            return new UnblockOutcome(accounts.unblock(id), null);
        }
        return new UnblockOutcome(null, row(open(ApprovalKind.ACCOUNT_UNBLOCK, id.value(), null, note, me), me));
    }

    /**
     * O que a pessoa enxerga: a Diretoria vê tudo; gerente, os pedidos do departamento dele; analista, só os que ele fez.
     * {@code status} nulo traz todos os estados.
     */
    @Transactional(readOnly = true)
    public List<Row> list(ApprovalStatus status) {
        User me = actor();
        List<ApprovalRequest> found;
        if (me.role() == Role.ADMIN) {
            found = approvals.find(status, null, null);
        } else if (me.isManager()) {
            found = approvals.find(status, kindsOf(me.department()), null);
        } else {
            found = approvals.find(status, null, me.id());
        }
        return found.stream().map(r -> row(r, me)).toList();
    }

    /** Quantos pedidos esperam a pessoa decidir (para o selo no menu); zero para quem não decide. */
    @Transactional(readOnly = true)
    public long pendingForMe() {
        User me = actor();
        if (me.role() == Role.ADMIN) {
            return approvals.countPending(EnumSet.allOf(ApprovalKind.class));
        }
        return me.isManager() ? approvals.countPending(kindsOf(me.department())) : 0;
    }

    public Row approve(UUID id, String note) {
        User me = actor();
        ApprovalRequest request = decidable(id, me);
        switch (request.kind()) {
            case CREDIT_LIMIT -> credit.changeLimitAsStaff(CardId.of(request.subjectId().toString()), request.amount());
            case ACCOUNT_UNBLOCK -> accounts.unblock(AccountId.of(request.subjectId().toString()));
        }
        request.approve(me.id(), note, time.now());
        approvals.save(request);
        audit.record(AuditEntry.of(AuditEvent.APPROVAL_APPROVED).detail(describe(request)));
        return row(request, me);
    }

    public Row reject(UUID id, String note) {
        User me = actor();
        ApprovalRequest request = decidable(id, me);
        request.reject(me.id(), note, time.now());
        approvals.save(request);
        audit.record(AuditEntry.of(AuditEvent.APPROVAL_REJECTED).detail(describe(request)));
        return row(request, me);
    }

    /** Só quem pediu desiste do próprio pedido. */
    public Row cancel(UUID id) {
        User me = actor();
        ApprovalRequest request = approvals.findById(id).orElseThrow(() -> ApplicationException.notFound("Approval"));
        if (!request.requestedBy().equals(me.id())) {
            throw ApplicationException.notFound("Approval"); // pedido de outra pessoa: não revelamos que existe
        }
        if (!request.isPending()) {
            throw ApplicationException.conflict("APPROVAL_NOT_PENDING", "The request was already decided");
        }
        request.cancel(me.id(), time.now());
        approvals.save(request);
        audit.record(AuditEntry.of(AuditEvent.APPROVAL_CANCELED).detail(describe(request)));
        return row(request, me);
    }

    private ApprovalRequest open(ApprovalKind kind, UUID subject, BigDecimal amount, String note, User me) {
        if (approvals.existsPending(kind, subject)) {
            throw ApplicationException.conflict("APPROVAL_ALREADY_PENDING", "There is already a pending request for this");
        }
        ApprovalRequest request = ApprovalRequest.open(kind, subject, amount, note, me.id(), time.now());
        approvals.save(request);
        audit.record(AuditEntry.of(AuditEvent.APPROVAL_REQUESTED).detail(describe(request)));
        return request;
    }

    /** O pedido, se a pessoa pode decidi-lo: do tipo do departamento dela (ou Diretoria), que não seja o dela. */
    private ApprovalRequest decidable(UUID id, User me) {
        ApprovalRequest request = approvals.findById(id).orElseThrow(() -> ApplicationException.notFound("Approval"));
        if (!ApprovalPolicy.canDecide(request.kind(), me.role(), me.department(), me.level())) {
            throw ApplicationException.forbidden();
        }
        if (request.requestedBy().equals(me.id())) {
            throw ApplicationException.conflict("CANNOT_DECIDE_OWN_REQUEST", "You cannot decide your own request");
        }
        if (!request.isPending()) {
            throw ApplicationException.conflict("APPROVAL_NOT_PENDING", "The request was already decided");
        }
        return request;
    }

    private Row row(ApprovalRequest r, User me) {
        String requester = emailOf(r.requestedBy());
        String decider = r.decidedBy() == null ? null : emailOf(r.decidedBy());
        boolean canDecide = r.isPending() && !r.requestedBy().equals(me.id())
                && ApprovalPolicy.canDecide(r.kind(), me.role(), me.department(), me.level());
        boolean canCancel = r.isPending() && r.requestedBy().equals(me.id());
        return switch (r.kind()) {
            case CREDIT_LIMIT -> {
                CreditApplicationService.StaffView card = credit.staffViewOf(CardId.of(r.subjectId().toString()));
                yield new Row(r, requester, decider, "Cartão final " + card.card().last4() + ", limite atual R$ "
                        + card.terms().limit().amount().toPlainString(), card.card().customerId().value(), canDecide, canCancel);
            }
            case ACCOUNT_UNBLOCK -> {
                Account account = accounts.getAny(AccountId.of(r.subjectId().toString()));
                yield new Row(r, requester, decider, "Conta " + account.branch() + "/" + account.accountNumber(),
                        account.customerId().value(), canDecide, canCancel);
            }
        };
    }

    private String emailOf(UserId id) {
        return users.findById(id).map(u -> u.email().value()).orElse("—");
    }

    private User actor() {
        UserId id = actors.current().userId();
        if (id == null) {
            throw ApplicationException.unauthenticated();
        }
        return users.findById(id).filter(u -> u.department() != null).orElseThrow(ApplicationException::forbidden);
    }

    private static Set<ApprovalKind> kindsOf(Department department) {
        Set<ApprovalKind> kinds = EnumSet.noneOf(ApprovalKind.class);
        Arrays.stream(ApprovalKind.values()).filter(k -> k.department() == department).forEach(kinds::add);
        return kinds;
    }

    private static String describe(ApprovalRequest r) {
        return r.id() + " " + r.kind() + " " + r.subjectId() + (r.amount() == null ? "" : " " + r.amount().toPlainString());
    }
}
