package com.ecommerce.user.service;

import com.ecommerce.user.domain.User;
import com.ecommerce.user.exception.UserNotFoundException;
import com.ecommerce.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Handles user-related business logic: creation, retrieval, and queries.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Registers a new user in the system.
     *
     * @param user the user to create
     * @return the persisted user with generated ID
     */
    public User createUser(User user) {
        log.info("create_user email={}", user.getEmail());
        return userRepository.save(user);
    }

    /**
     * Retrieves a user by their unique identifier.
     *
     * @param id the user ID
     * @return the matching user
     * @throws UserNotFoundException if no user exists with the given ID
     */
    public User getUserById(Long id) {
        log.info("get_user_by_id id={}", id);
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    /**
     * Returns all registered users.
     *
     * @return list of all users in the system
     */
    public List<User> getAllUsers() {
        log.info("get_all_users");
        return userRepository.findAll();
    }
}
