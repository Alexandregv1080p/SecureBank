package com.securebank.piggy.application;

import com.securebank.piggy.domain.Piggy;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.PiggyId;
import java.util.List;
import java.util.Optional;

public interface PiggyRepository {

    Optional<Piggy> findById(PiggyId id);

    /** Porquinhos ativos do cliente, do mais antigo para o mais novo. */
    List<Piggy> findActiveByCustomer(CustomerId customerId);

    long countActiveByCustomer(CustomerId customerId);

    /** A versão otimista é conferida no flush (conflito → 409, e a operação é repetida). */
    void save(Piggy piggy);
}
