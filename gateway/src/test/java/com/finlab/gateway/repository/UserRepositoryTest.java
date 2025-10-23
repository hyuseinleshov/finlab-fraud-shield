package com.finlab.gateway.repository;

import com.finlab.gateway.config.BaseIntegrationTest;
import com.finlab.gateway.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest extends BaseIntegrationTest {

    // Test user data constants
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_EMAIL = "test@finlab.bg";
    private static final String NONEXISTENT_USERNAME = "nonexistent";
    private static final String NONEXISTENT_EMAIL = "nonexistent@finlab.bg";

    // Expected values
    private static final int ZERO_FAILED_ATTEMPTS = 0;
    private static final int INCREMENT_BY_ONE = 1;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUsername_WithExistingUser_ShouldReturnUser() {
        User user = userRepository.findByUsername(TEST_USERNAME);

        assertNotNull(user);
        assertEquals(TEST_USERNAME, user.getUsername());
        assertEquals(TEST_EMAIL, user.getEmail());
        assertNotNull(user.getPasswordHash());
        assertTrue(user.isActive());
        assertFalse(user.isLocked());
    }

    @Test
    void findByUsername_WithNonExistentUser_ShouldReturnNull() {
        User user = userRepository.findByUsername(NONEXISTENT_USERNAME);

        assertNull(user);
    }

    @Test
    void findByEmail_WithExistingEmail_ShouldReturnUser() {
        User user = userRepository.findByEmail(TEST_EMAIL);

        assertNotNull(user);
        assertEquals(TEST_USERNAME, user.getUsername());
        assertEquals(TEST_EMAIL, user.getEmail());
    }

    @Test
    void findByEmail_WithNonExistentEmail_ShouldReturnNull() {
        User user = userRepository.findByEmail(NONEXISTENT_EMAIL);

        assertNull(user);
    }

    @Test
    void updateLastLogin_ShouldUpdateTimestamp() {
        userRepository.updateLastLogin(TEST_USERNAME);

        User userAfter = userRepository.findByUsername(TEST_USERNAME);
        assertNotNull(userAfter);
        assertEquals(ZERO_FAILED_ATTEMPTS, userAfter.getFailedLoginAttempts());
    }

    @Test
    void incrementFailedLoginAttempts_ShouldIncreaseCounter() {
        User userBefore = userRepository.findByUsername(TEST_USERNAME);
        assertNotNull(userBefore);
        int attemptsBefore = userBefore.getFailedLoginAttempts();

        userRepository.incrementFailedLoginAttempts(TEST_USERNAME);

        User userAfter = userRepository.findByUsername(TEST_USERNAME);
        assertNotNull(userAfter);
        assertEquals(attemptsBefore + INCREMENT_BY_ONE, userAfter.getFailedLoginAttempts());
    }
}
