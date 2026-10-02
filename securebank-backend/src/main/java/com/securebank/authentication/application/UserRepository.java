package com.securebank.authentication.application;

import com.securebank.authentication.domain.User;
import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.UserId;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(Email email);

    boolean existsByEmail(Email email);

    void save(User user);
}
