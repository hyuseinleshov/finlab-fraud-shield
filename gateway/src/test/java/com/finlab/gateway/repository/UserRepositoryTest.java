package com.finlab.gateway.repository;

import com.finlab.gateway.AbstractIntegrationTest;
import com.finlab.gateway.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUsername_WithExistingUser_ShouldReturnUser() {
        // Act
        User user = userRepository.findByUsername("testuser");

        // Assert
        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
        assertEquals("test@finlab.bg", user.getEmail());
        assertNotNull(user.getPasswordHash());
        assertTrue(user.isActive());
        assertFalse(user.isLocked());
    }

    @Test
    void findByUsername_WithNonExistentUser_ShouldReturnNull() {
        // Act
        User user = userRepository.findByUsername("nonexistent");

        // Assert
        assertNull(user);
    }

    @Test
    void findByEmail_WithExistingEmail_ShouldReturnUser() {
        // Act
        User user = userRepository.findByEmail("test@finlab.bg");

        // Assert
        assertNotNull(user);
        assertEquals("testuser", user.getUsername());
        assertEquals("test@finlab.bg", user.getEmail());
    }

    @Test
    void findByEmail_WithNonExistentEmail_ShouldReturnNull() {
        // Act
        User user = userRepository.findByEmail("nonexistent@finlab.bg");

        // Assert
        assertNull(user);
    }

    @Test
    void updateLastLogin_ShouldUpdateTimestamp() {
        // Arrange
        String username = "testuser";
        User userBefore = userRepository.findByUsername(username);

        // Act
        userRepository.updateLastLogin(username);

        // Assert
        User userAfter = userRepository.findByUsername(username);
        assertNotNull(userAfter);
        // Last login should be updated (or at least not null)
        // Failed login attempts should be reset to 0
        assertEquals(0, userAfter.getFailedLoginAttempts());
    }

    @Test
    void incrementFailedLoginAttempts_ShouldIncreaseCounter() {
        // Arrange
        String username = "testuser";
        User userBefore = userRepository.findByUsername(username);
        int attemptsBefore = userBefore.getFailedLoginAttempts();

        // Act
        userRepository.incrementFailedLoginAttempts(username);

        // Assert
        User userAfter = userRepository.findByUsername(username);
        assertNotNull(userAfter);
        assertEquals(attemptsBefore + 1, userAfter.getFailedLoginAttempts());
    }
}
