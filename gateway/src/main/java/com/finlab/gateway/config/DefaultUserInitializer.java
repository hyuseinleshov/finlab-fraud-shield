package com.finlab.gateway.config;

import com.finlab.gateway.model.User;
import com.finlab.gateway.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Initializes default users on application startup.
 * Uses application's PasswordEncoder to ensure hash compatibility.
 */
@Component
public class DefaultUserInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String defaultPassword;

    public DefaultUserInitializer(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${app.default-password:password123}") String defaultPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
    }

    @Override
    public void run(String... args) {
        log.info("Initializing default users...");

        createUserIfNotExists("admin", "admin@finlab.bg", "Administrator", true);
        createUserIfNotExists("testuser", "test@finlab.bg", "Test User", true);
        createUserIfNotExists("demo", "demo@finlab.bg", "Demo Account", true);
        createUserIfNotExists("inactive", "inactive@finlab.bg", "Inactive User", false);

        log.info("Default users initialization completed");
    }

    private void createUserIfNotExists(String username, String email, String fullName, boolean isActive) {
        User existingUser = userRepository.findByUsername(username);

        if (existingUser == null) {
            String hashedPassword = passwordEncoder.encode(defaultPassword);

            User newUser = new User();
            newUser.setUsername(username);
            newUser.setEmail(email);
            newUser.setPasswordHash(hashedPassword);
            newUser.setFullName(fullName);
            newUser.setActive(isActive);
            newUser.setLocked(false);
            newUser.setFailedLoginAttempts(0);

            userRepository.save(newUser);
            log.info("Created default user: {} (active: {})", username, isActive);
        } else {
            log.debug("User already exists: {}", username);
        }
    }
}
