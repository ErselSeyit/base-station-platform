package com.huawei.auth.config;

import com.huawei.auth.repository.UserRepository;
import com.huawei.auth.service.UserService;
import com.huawei.common.security.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Loads initial data on application startup.
 */
@Configuration
public class DataLoader {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    @Value("${auth.admin.username:admin}")
    private String adminUsername;

    @Value("${auth.admin.password:#{null}}")
    private String adminPassword;

    @Value("${auth.bridge.username:bridge-user}")
    private String bridgeUsername;

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository, UserService userService) {
        return args -> {
            // Validate password is configured
            if (adminPassword == null || adminPassword.isBlank()) {
                log.warn("No admin password configured (AUTH_ADMIN_PASSWORD). " +
                        "Skipping user creation. " +
                        "Set AUTH_ADMIN_PASSWORD environment variable to create users.");
                return;
            }

            if (adminPassword.length() < 12) {
                log.error("Admin password must be at least 12 characters for security. " +
                        "Skipping user creation.");
                return;
            }

            // Create or update admin user - always sync password from config
            createOrUpdateUser(userRepository, userService, adminUsername, adminPassword, Roles.ROLE_ADMIN);

            // Create or update bridge service account
            createOrUpdateUser(userRepository, userService, bridgeUsername, adminPassword, Roles.ROLE_SERVICE);
        };
    }

    /**
     * Creates a user if not exists, or updates the password if it does.
     * This ensures passwords stay in sync with the configured values.
     */
    private void createOrUpdateUser(UserRepository userRepository, UserService userService,
                                     String username, String password, String role) {
        if (!userRepository.existsByUsername(username)) {
            userService.createUser(username, password, role);
            log.info("Created user: {} with role: {}", username, role);
        } else {
            // Update password to ensure it matches the configured value
            userService.updatePassword(username, password);
            log.info("Updated password for user: {}", username);
        }
    }
}
