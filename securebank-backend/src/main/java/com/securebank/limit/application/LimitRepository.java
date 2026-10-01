package com.securebank.limit.application;

import com.securebank.limit.domain.Limit;
import com.securebank.limit.domain.LimitType;
import com.securebank.shared.domain.AccountId;
import java.util.List;
import java.util.Optional;

public interface LimitRepository {

    Optional<Limit> find(AccountId accountId, LimitType type);

    List<Limit> findAll(AccountId accountId);

    void save(Limit limit);
}
