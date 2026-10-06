package com.securebank.pix.application;

import com.securebank.pix.domain.PixKey;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.PixKeyId;
import java.util.List;
import java.util.Optional;

public interface PixKeyRepository {

    Optional<PixKey> findById(PixKeyId id);

    Optional<PixKey> findByValue(String value);

    List<PixKey> findByCustomer(CustomerId customerId);

    long countByCustomer(CustomerId customerId);

    void save(PixKey key);

    void delete(PixKeyId id);
}
