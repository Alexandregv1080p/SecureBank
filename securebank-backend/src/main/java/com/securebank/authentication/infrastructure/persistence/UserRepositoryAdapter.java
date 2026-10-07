package com.securebank.authentication.infrastructure.persistence;

import com.securebank.authentication.application.UserRepository;
import com.securebank.authentication.domain.User;
import com.securebank.shared.domain.Email;
import com.securebank.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
class UserRepositoryAdapter implements UserRepository {

    private final EntityManager em;

    UserRepositoryAdapter(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(em.find(UserEntity.class, id.value())).map(UserEntity::toDomain);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return em.createQuery("select u from UserEntity u where u.email = :email", UserEntity.class)
                .setParameter("email", email.value()).getResultStream().findFirst().map(UserEntity::toDomain);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return em.createQuery("select count(u) from UserEntity u where u.email = :email", Long.class)
                .setParameter("email", email.value()).getSingleResult() > 0;
    }

    @Override
    public java.util.List<User> findStaff() {
        return em.createQuery("select u from UserEntity u where u.role <> :customer order by u.createdAt, u.id",
                        UserEntity.class)
                .setParameter("customer", com.securebank.authorization.domain.Role.CUSTOMER)
                .getResultList().stream().map(UserEntity::toDomain).toList();
    }

    @Override
    public void save(User user) {
        UserEntity entity = em.find(UserEntity.class, user.id().value());
        if (entity == null) {
            entity = new UserEntity();
            entity.apply(user);
            em.persist(entity);
        } else {
            entity.apply(user);
        }
    }
}
