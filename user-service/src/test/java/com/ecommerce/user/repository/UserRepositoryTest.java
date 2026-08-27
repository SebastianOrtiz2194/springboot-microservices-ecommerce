package com.ecommerce.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ecommerce.user.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Repository slice tests for {@link UserRepository} against a real PostgreSQL container with Flyway
 * migrations applied.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserRepositoryTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private UserRepository userRepository;

    @Test
    void findByEmail_returnsSeededUser() {
        assertThat(userRepository.findByEmail("john@example.com"))
                .map(User::getEmail)
                .contains("john@example.com");
    }

    @Test
    void findByEmail_returnsEmptyForUnknownEmail() {
        assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void save_persistsUserWithGeneratedId() {
        User user = new User("Alice", "alice@example.com", "hashed", "USER");

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(userRepository.findById(saved.getId())).contains(saved);
    }

    @Test
    void save_duplicateEmail_throwsDataIntegrityViolation() {
        userRepository.saveAndFlush(new User("Bob", "bob@example.com", "hashed", "USER"));

        assertThatThrownBy(
                        () ->
                                userRepository.saveAndFlush(
                                        new User("Another", "bob@example.com", "hashed", "USER")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
