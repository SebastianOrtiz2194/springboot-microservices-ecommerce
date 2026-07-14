package com.ecommerce.user.repository;

import com.ecommerce.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link User} entities.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their unique email address.
     *
     * @param email the email to search by
     * @return an {@link Optional} containing the user if found
     */
    Optional<User> findByEmail(String email);
}
