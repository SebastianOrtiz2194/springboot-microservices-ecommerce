package com.ecommerce.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.user.domain.User;
import com.ecommerce.user.exception.UserNotFoundException;
import com.ecommerce.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link UserService}. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private UserService userService;

    @Test
    void createUser_savesAndReturnsUser() {
        User user = new User("Alice", "alice@example.com", "hashed", "USER");

        when(userRepository.save(user)).thenReturn(user);

        User result = userService.createUser(user);

        assertThat(result).isSameAs(user);
        verify(userRepository).save(user);
    }

    @Test
    void getUserById_returnsUserWhenFound() {
        User user = new User("Alice", "alice@example.com", "hashed", "USER");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        User result = userService.getUserById(1L);

        assertThat(result).isEqualTo(user);
    }

    @Test
    void getUserById_throwsWhenNotFound() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUserById(42L))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void getAllUsers_returnsAllUsers() {
        List<User> users = List.of(new User("Alice", "alice@example.com", "hashed", "USER"));
        when(userRepository.findAll()).thenReturn(users);

        List<User> result = userService.getAllUsers();

        assertThat(result).containsExactlyElementsOf(users);
    }
}
