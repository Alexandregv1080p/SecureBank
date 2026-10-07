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

    /**
     * Marca como avisadas (e devolve) até [limit] aplicações com prazo vencidas e ainda ativas. Atômico e seguro com
     * várias instâncias (SKIP LOCKED): cada vencimento é avisado uma vez só.
     */
    List<MaturedInvestment> claimMatured(java.time.Instant now, int limit);

    /** A versão otimista é conferida no flush: dois resgates simultâneos da mesma aplicação não passam os dois. */
    void save(Investment investment);
}
