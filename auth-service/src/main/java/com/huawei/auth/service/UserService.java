package com.huawei.auth.service;

import com.huawei.auth.model.User;
import com.huawei.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static com.huawei.common.config.CacheConfig.USERS_CACHE;

/**
 * Service for user management and authentication.
 */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Authenticates a user by username and password.
     *
     * @param username the username
     * @param password the raw password
     * @return Optional containing the user if authentication succeeds
     */
    public Optional<User> authenticate(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }

        return userRepository.findByUsernameAndEnabledTrue(username)
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()));
    }

    /**
     * Creates a new user with encoded password.
     *
     * @param username the username
     * @param rawPassword the raw password to encode
     * @param role the user role
     * @return the created user
     * @throws IllegalArgumentException if username already exists
     */
    @Transactional
    public User createUser(String username, String rawPassword, String role) {
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setEnabled(true);

        User saved = userRepository.save(user);
        log.info("Created new user: {}", username);
        return saved;
    }

    /**
     * Finds a user by username (cached).
     *
     * @param username the username
     * @return Optional containing the user if found
     */
    @Cacheable(value = USERS_CACHE, key = "#username", unless = "#result.isEmpty()")
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Updates user password.
     *
     * @param username the username
     * @param newPassword the new raw password
     */
    @Transactional
    @CacheEvict(value = USERS_CACHE, key = "#username")
    public void updatePassword(String username, String newPassword) {
        userRepository.findByUsername(username)
                .ifPresent(user -> {
                    user.setPasswordHash(passwordEncoder.encode(newPassword));
                    userRepository.save(user);
                    log.info("Password updated for user: {}", username);
                });
    }

    /**
     * Disables a user account.
     *
     * @param username the username
     */
    @Transactional
    @CacheEvict(value = USERS_CACHE, key = "#username")
    public void disableUser(String username) {
        userRepository.findByUsername(username)
                .ifPresent(user -> {
                    user.setEnabled(false);
                    userRepository.save(user);
                    log.info("Disabled user: {}", username);
                });
    }
}
