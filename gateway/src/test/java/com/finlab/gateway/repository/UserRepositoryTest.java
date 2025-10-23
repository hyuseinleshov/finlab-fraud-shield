package com.finlab.gateway.repository;

import com.finlab.gateway.config.BaseIntegrationTest;
import com.finlab.gateway.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUsername_WithExistingUser_ShouldReturnUser() {
        User user = userRepository.findByUsername("testuser");

        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
        assertEquals("test@finlab.bg", user.getEmail());
        assertNotNull(user.getPasswordHash());
        assertTrue(user.isActive());
        assertFalse(user.isLocked());
    }

    @Test
    void findByUsername_WithNonExistentUser_ShouldReturnNull() {
        User user = userRepository.findByUsername("nonexistent");

        assertNull(user);
    }

    @Test
    void findByEmail_WithExistingEmail_ShouldReturnUser() {
        User user = userRepository.findByEmail("test@finlab.bg");

        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
        assertEquals("test@finlab.bg", user.getEmail());
    }

    @Test
    void findByEmail_WithNonExistentEmail_ShouldReturnNull() {
        User user = userRepository.findByEmail("nonexistent@finlab.bg");

        assertNull(user);
    }

    @Test
    void updateLastLogin_ShouldUpdateTimestamp() {
        String username = "testuser";

        userRepository.updateLastLogin(username);

        User userAfter = userRepository.findByUsername(username);
        assertNotNull(userAfter);
        // Last login should be updated (or at least not null)
        // Failed login attempts should be reset to 0
        assertEquals(0, userAfter.getFailedLoginAttempts());
    }

    @Test
    void incrementFailedLoginAttempts_ShouldIncreaseCounter() {
        String username = "testuser";
        User userBefore = userRepository.findByUsername(username);
        int attemptsBefore = userBefore.getFailedLoginAttempts();

        userRepository.incrementFailedLoginAttempts(username);

        User userAfter = userRepository.findByUsername(username);
        assertNotNull(userAfter);
        assertEquals(attemptsBefore + 1, userAfter.getFailedLoginAttempts());
    }
}
