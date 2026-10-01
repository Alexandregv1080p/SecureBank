package com.securebank.account.application;

import com.securebank.account.domain.Account;
import com.securebank.account.domain.AccountNumber;
import com.securebank.account.domain.Branch;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CustomerId;
import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    Optional<Account> findById(AccountId id);

    Optional<Account> findByBranchAndNumber(Branch branch, AccountNumber number);

    List<Account> findByCustomer(CustomerId customerId);

    /** Persiste o estado do aggregate; a versão otimista é conferida no flush (conflito → 409). */
    void save(Account account);
}
