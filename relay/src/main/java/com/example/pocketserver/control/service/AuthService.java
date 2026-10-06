package com.example.pocketserver.control.service;

import com.example.pocketserver.control.model.User;
import com.example.pocketserver.control.repository.ControlPlaneRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing user registration, authentication, password hashing with salt,
 * and session Bearer token issuance.
 */
public class AuthService {

    private final ControlPlaneRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    // Map: bearerToken -> userId
    private final ConcurrentHashMap<String, String> tokenToUserId = new ConcurrentHashMap<>();

    public AuthService(ControlPlaneRepository repository) {
        this.repository = repository;
    }

    public static class AuthResult {
        private final User user;
        private final String token;

        public AuthResult(User user, String token) {
            this.user = user;
            this.token = token;
        }

        public User getUser() {
            return user;
        }

        public String getToken() {
            return token;
        }
    }

    /**
     * Registers a new user account with email and password.
     */
    public AuthResult register(String email, String password) {
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format");
        }
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters");
        }

        if (repository.findUserByEmail(email) != null) {
            throw new IllegalStateException("An account with this email already exists");
        }

        String salt = generateSalt();
        String passwordHash = hashPassword(password, salt);
        String userId = "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        User user = new User(userId, email.trim(), passwordHash, salt, System.currentTimeMillis());
        repository.saveUser(user);

        String token = generateToken(userId);
        return new AuthResult(user, token);
    }

    /**
     * Authenticates existing user with email and password.
     */
    public AuthResult login(String email, String password) {
        if (email == null || password == null) {
            throw new IllegalArgumentException("Email and password are required");
        }

        User user = repository.findUserByEmail(email);
        if (user == null) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String expectedHash = hashPassword(password, user.getSalt());
        if (!MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.UTF_8),
                user.getPasswordHash().getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String token = generateToken(user.getId());
        return new AuthResult(user, token);
    }

    /**
     * Authenticates a Bearer token and returns the User.
     */
    public User authenticateToken(String bearerToken) {
        if (bearerToken == null || bearerToken.isEmpty()) {
            return null;
        }
        String cleanToken = bearerToken.startsWith("Bearer ") ? bearerToken.substring(7).trim() : bearerToken.trim();
        String userId = tokenToUserId.get(cleanToken);
        return userId != null ? repository.findUserById(userId) : null;
    }

    private String generateToken(String userId) {
        String token = "pst_" + UUID.randomUUID().toString().replace("-", "");
        tokenToUserId.put(token, userId);
        return token;
    }

    private String generateSalt() {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
