package com.securebank.investment.application;

import com.securebank.investment.domain.Investment;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.InvestmentId;
import java.util.List;
import java.util.Optional;

public interface InvestmentRepository {

    Optional<Investment> findById(InvestmentId id);

    /** Aplicações do cliente, ativas primeiro e da mais nova para a mais antiga (até 200). */
    List<Investment> findByCustomer(CustomerId customerId);

    long countActiveByCustomer(CustomerId customerId);

    /** A versão otimista é conferida no flush: dois resgates simultâneos da mesma aplicação não passam os dois. */
    void save(Investment investment);
}
