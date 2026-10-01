package com.securebank.account.infrastructure.persistence;

import com.securebank.account.application.AccountNumberGenerator;
import com.securebank.account.domain.AccountNumber;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
class SequenceAccountNumberGenerator implements AccountNumberGenerator {

    private final EntityManager em;

    SequenceAccountNumberGenerator(EntityManager em) {
        this.em = em;
    }

    @Override
    public AccountNumber next() {
        Number sequence = (Number) em.createNativeQuery("select nextval('account_number_seq')").getSingleResult();
        return AccountNumber.generate(sequence.longValue());
    }
}
