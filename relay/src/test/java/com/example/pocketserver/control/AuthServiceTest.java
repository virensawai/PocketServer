package com.example.pocketserver.control;

import com.example.pocketserver.control.model.User;
import com.example.pocketserver.control.repository.ControlPlaneRepository;
import com.example.pocketserver.control.service.AuthService;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class AuthServiceTest {

    private ControlPlaneRepository repository;
    private AuthService authService;

    @Before
    public void setUp() {
        repository = new ControlPlaneRepository();
        authService = new AuthService(repository);
    }

    @Test
    public void testRegisterAndLoginSuccess() {
        AuthService.AuthResult regResult = authService.register("developer@example.com", "secure123");
        assertNotNull(regResult);
        assertNotNull(regResult.getUser());
        assertEquals("developer@example.com", regResult.getUser().getEmail());
        assertNotNull(regResult.getToken());

        // Authenticate with token
        User authenticated = authService.authenticateToken(regResult.getToken());
        assertNotNull(authenticated);
        assertEquals(regResult.getUser().getId(), authenticated.getId());

        // Login with credentials
        AuthService.AuthResult loginResult = authService.login("developer@example.com", "secure123");
        assertNotNull(loginResult);
        assertEquals(regResult.getUser().getId(), loginResult.getUser().getId());
    }

    @Test
    public void testDuplicateEmailRejected() {
        authService.register("dup@example.com", "password123");
        try {
            authService.register("dup@example.com", "password456");
            fail("Expected exception for duplicate email");
        } catch (IllegalStateException e) {
            assertEquals("An account with this email already exists", e.getMessage());
        }
    }

    @Test
    public void testInvalidPasswordFailsLogin() {
        authService.register("dev2@example.com", "password123");
        try {
            authService.login("dev2@example.com", "wrongpassword");
            fail("Expected exception for bad password");
        } catch (IllegalArgumentException e) {
            assertEquals("Invalid email or password", e.getMessage());
        }
    }

    @Test
    public void testInvalidTokenReturnsNull() {
        assertNull(authService.authenticateToken("invalid-token"));
        assertNull(authService.authenticateToken(""));
        assertNull(authService.authenticateToken(null));
    }
}
