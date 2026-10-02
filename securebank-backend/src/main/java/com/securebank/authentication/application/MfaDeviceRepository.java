package com.securebank.authentication.application;

import com.securebank.authentication.domain.MfaDevice;
import com.securebank.shared.domain.UserId;
import java.util.Optional;

public interface MfaDeviceRepository {

    Optional<MfaDevice> findByUser(UserId userId);

    void save(MfaDevice device);

    void deleteByUser(UserId userId);
}
