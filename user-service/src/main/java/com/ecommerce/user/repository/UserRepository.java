package com.ecommerce.user.repository;

import com.ecommerce.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link User} entities. */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user by their unique email address.
     *
     * @param email the email to search by
     * @return an {@link Optional} containing the user if found
     */
    Optional<User> findByEmail(String email);
}
