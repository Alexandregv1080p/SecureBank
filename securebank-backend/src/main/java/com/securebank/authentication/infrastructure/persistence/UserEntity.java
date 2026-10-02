package com.securebank.authentication.infrastructure.persistence;

import com.securebank.authentication.domain.User;
import com.securebank.authentication.domain.UserStatus;
import com.securebank.authorization.domain.Role;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.UserId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
class UserEntity {

    @Id UUID id;
    String email;
    String passwordHash;
    @Enumerated(EnumType.STRING) Role role;
    UUID customerId;
    @Enumerated(EnumType.STRING) UserStatus status;
    Instant createdAt;
    Instant updatedAt;
    @Version Long version;

    void apply(User user) {
        id = user.id().value();
        email = user.email().value();
        passwordHash = user.passwordHash();
        role = user.role();
        customerId = user.customerId() == null ? null : user.customerId().value();
        status = user.status();
        createdAt = user.createdAt();
        updatedAt = user.updatedAt();
    }

    User toDomain() {
        return User.restore(new UserId(id), new Email(email), passwordHash, role,
                customerId == null ? null : new CustomerId(customerId), status, createdAt, updatedAt);
    }
}
