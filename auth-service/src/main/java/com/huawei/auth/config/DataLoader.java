package com.huawei.auth.config;

import com.huawei.auth.repository.UserRepository;
import com.huawei.auth.service.UserService;
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

    @Bean
    CommandLineRunner initDatabase(UserRepository userRepository, UserService userService) {
        return args -> {
            // Create default admin user if not exists and password is configured
            if (!userRepository.existsByUsername(adminUsername)) {
                if (adminPassword == null || adminPassword.isBlank()) {
                    log.warn("No admin password configured (AUTH_ADMIN_PASSWORD). " +
                            "Skipping default admin user creation. " +
                            "Set AUTH_ADMIN_PASSWORD environment variable to create admin user.");
                    return;
                }

                if (adminPassword.length() < 12) {
                    log.error("Admin password must be at least 12 characters. " +
                            "Skipping default admin user creation.");
                    return;
                }

                userService.createUser(adminUsername, adminPassword, "ROLE_ADMIN");
                log.info("Created default admin user: {}", adminUsername);
            } else {
                log.debug("Admin user already exists: {}", adminUsername);
            }
        };
    }
}
