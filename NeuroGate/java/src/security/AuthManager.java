package security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * AuthManager handles user identity and encryption policies.
 */
public class AuthManager {
    private static final Logger logger = Logger.getLogger(AuthManager.class.getName());
    
    private final Map<String, User> users;
    private final Map<String, Session> sessions;
    private final SecureRandom random;
    
    /**
     * Create a new AuthManager.
     */
    public AuthManager() {
        this.users = new ConcurrentHashMap<>();
        this.sessions = new ConcurrentHashMap<>();
        this.random = new SecureRandom();
        
        // Create default admin user
        createUser("admin", "admin", UserRole.ADMIN);
    }
    
    /**
     * Create a new user.
     * 
     * @param username The username
     * @param password The password
     * @param role The user role
     * @return true if user creation was successful
     */
    public boolean createUser(String username, String password, UserRole role) {
        if (username == null || password == null || role == null) {
            logger.warning("Invalid parameters for createUser");
            return false;
        }
        
        if (users.containsKey(username)) {
            logger.warning("User " + username + " already exists");
            return false;
        }
        
        // Generate salt
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        
        // Hash password
        String passwordHash = hashPassword(password, salt);
        
        // Create user
        User user = new User(username, passwordHash, Base64.getEncoder().encodeToString(salt), role);
        users.put(username, user);
        
        logger.info("Created user " + username + " with role " + role);
        return true;
    }
    
    /**
     * Authenticate a user.
     * 
     * @param username The username
     * @param password The password
     * @return Session token if authentication was successful, null otherwise
     */
    public String authenticate(String username, String password) {
        if (username == null || password == null) {
            logger.warning("Invalid parameters for authenticate");
            return null;
        }
        
        User user = users.get(username);
        if (user == null) {
            logger.warning("User " + username + " not found");
            return null;
        }
        
        // Decode salt
        byte[] salt = Base64.getDecoder().decode(user.getSalt());
        
        // Hash password
        String passwordHash = hashPassword(password, salt);
        
        // Check password
        if (!passwordHash.equals(user.getPasswordHash())) {
            logger.warning("Invalid password for user " + username);
            return null;
        }
        
        // Generate session token
        String token = UUID.randomUUID().toString();
        
        // Create session
        Session session = new Session(token, username, System.currentTimeMillis());
        sessions.put(token, session);
        
        logger.info("User " + username + " authenticated successfully");
        return token;
    }
    
    /**
     * Validate a session token.
     * 
     * @param token The session token
     * @return true if the token is valid
     */
    public boolean validateToken(String token) {
        if (token == null) {
            return false;
        }
        
        Session session = sessions.get(token);
        if (session == null) {
            return false;
        }
        
        // Check if session has expired (1 hour timeout)
        long now = System.currentTimeMillis();
        if (now - session.getCreatedAt() > 3600000) {
            sessions.remove(token);
            return false;
        }
        
        return true;
    }
    
    /**
     * Get the username associated with a session token.
     * 
     * @param token The session token
     * @return The username or null if the token is invalid
     */
    public String getUsernameForToken(String token) {
        if (!validateToken(token)) {
            return null;
        }
        
        Session session = sessions.get(token);
        return session.getUsername();
    }
    
    /**
     * Check if a user has a specific role.
     * 
     * @param username The username
     * @param role The role to check
     * @return true if the user has the role
     */
    public boolean hasRole(String username, UserRole role) {
        if (username == null || role == null) {
            return false;
        }
        
        User user = users.get(username);
        if (user == null) {
            return false;
        }
        
        return user.getRole() == role;
    }
    
    /**
     * Invalidate a session token.
     * 
     * @param token The session token
     */
    public void invalidateToken(String token) {
        if (token == null) {
            return;
        }
        
        Session session = sessions.remove(token);
        if (session != null) {
            logger.info("Invalidated session for user " + session.getUsername());
        }
    }
    
    /**
     * Get information about all users.
     * 
     * @return Map of usernames to user info
     */
    public Map<String, Map<String, Object>> getUserInfo() {
        Map<String, Map<String, Object>> userInfo = new HashMap<>();
        
        for (User user : users.values()) {
            Map<String, Object> info = new HashMap<>();
            info.put("username", user.getUsername());
            info.put("role", user.getRole().toString());
            userInfo.put(user.getUsername(), info);
        }
        
        return userInfo;
    }
    
    /**
     * Generate API keys for a user.
     * 
     * @param username The username
     * @return The generated API key or null if generation failed
     */
    public String generateApiKey(String username) {
        if (username == null) {
            logger.warning("Invalid username for generateApiKey");
            return null;
        }
        
        User user = users.get(username);
        if (user == null) {
            logger.warning("User " + username + " not found");
            return null;
        }
        
        // Generate API key
        byte[] keyBytes = new byte[32];
        random.nextBytes(keyBytes);
        String apiKey = Base64.getEncoder().encodeToString(keyBytes);
        
        // Update user
        user.setApiKey(apiKey);
        
        logger.info("Generated API key for user " + username);
        return apiKey;
    }
    
    /**
     * Validate an API key.
     * 
     * @param apiKey The API key
     * @return The username associated with the API key, or null if invalid
     */
    public String validateApiKey(String apiKey) {
        if (apiKey == null) {
            return null;
        }
        
        for (User user : users.values()) {
            if (apiKey.equals(user.getApiKey())) {
                return user.getUsername();
            }
        }
        
        return null;
    }
    
    /**
     * Hash a password with salt.
     * 
     * @param password The password
     * @param salt The salt
     * @return The password hash
     */
    private String hashPassword(String password, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hashedPassword = md.digest(password.getBytes());
            return Base64.getEncoder().encodeToString(hashedPassword);
        } catch (NoSuchAlgorithmException e) {
            logger.severe("Error hashing password: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * User roles.
     */
    public enum UserRole {
        ADMIN,
        USER,
        GUEST
    }
    
    /**
     * User information.
     */
    private static class User {
        private final String username;
        private final String passwordHash;
        private final String salt;
        private final UserRole role;
        private String apiKey;
        
        public User(String username, String passwordHash, String salt, UserRole role) {
            this.username = username;
            this.passwordHash = passwordHash;
            this.salt = salt;
            this.role = role;
            this.apiKey = null;
        }
        
        public String getUsername() {
            return username;
        }
        
        public String getPasswordHash() {
            return passwordHash;
        }
        
        public String getSalt() {
            return salt;
        }
        
        public UserRole getRole() {
            return role;
        }
        
        public String getApiKey() {
            return apiKey;
        }
        
        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }
    }
    
    /**
     * Session information.
     */
    private static class Session {
        private final String token;
        private final String username;
        private final long createdAt;
        
        public Session(String token, String username, long createdAt) {
            this.token = token;
            this.username = username;
            this.createdAt = createdAt;
        }
        
        public String getToken() {
            return token;
        }
        
        public String getUsername() {
            return username;
        }
        
        public long getCreatedAt() {
            return createdAt;
        }
    }
}