package com.securebank.limit.application;

import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.Money;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ajuste de limites por quem tem MANAGE_LIMITS (ADMIN). O cliente só consulta os seus. */
@Service
@Transactional
public class LimitApplicationService {

    private final LimitRepository limits;
    private final AuditService audit;
    private final BankTime time;

    public LimitApplicationService(LimitRepository limits, AuditService audit, BankTime time) {
        this.limits = limits;
        this.audit = audit;
        this.time = time;
    }

    public Limit change(AccountId accountId, LimitType type, BigDecimal perOperation, BigDecimal daily) {
        Limit limit = limits.find(accountId, type).orElseThrow(() -> ApplicationException.notFound("Limit"));
        var currency = limit.perOperation().currency();
        limit.change(new Money(perOperation, currency), new Money(daily, currency), time.now());
        limits.save(limit);
        audit.record(AuditEntry.of(AuditEvent.LIMIT_CHANGED).account(accountId)
                .detail(type + " " + limit.perOperation().amount().toPlainString() + "/"
                        + limit.daily().amount().toPlainString()));
        return limit;
    }
}
