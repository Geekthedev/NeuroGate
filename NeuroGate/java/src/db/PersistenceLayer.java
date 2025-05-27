package db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * PersistenceLayer handles storage of node logs and job configurations.
 */
public class PersistenceLayer {
    private static final Logger logger = Logger.getLogger(PersistenceLayer.class.getName());
    
    private final String dbPath;
    private Connection connection;
    
    /**
     * Create a new PersistenceLayer with the default database path.
     */
    public PersistenceLayer() {
        this("./neurogate.db");
    }
    
    /**
     * Create a new PersistenceLayer with a custom database path.
     * 
     * @param dbPath The path to the SQLite database file
     */
    public PersistenceLayer(String dbPath) {
        this.dbPath = dbPath;
    }
    
    /**
     * Initialize the persistence layer and create necessary tables.
     * 
     * @return true if initialization was successful
     */
    public boolean initialize() {
        try {
            // Create database directory if it doesn't exist
            File dbFile = new File(dbPath);
            File dbDir = dbFile.getParentFile();
            if (dbDir != null && !dbDir.exists()) {
                dbDir.mkdirs();
            }
            
            // Load SQLite JDBC driver
            Class.forName("org.sqlite.JDBC");
            
            // Connect to database
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            
            // Create tables if they don't exist
            createTables();
            
            logger.info("PersistenceLayer initialized with database: " + dbPath);
            return true;
        } catch (ClassNotFoundException e) {
            logger.severe("SQLite JDBC driver not found: " + e.getMessage());
            return false;
        } catch (SQLException e) {
            logger.severe("Error initializing database: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Close the database connection and clean up resources.
     */
    public void shutdown() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("PersistenceLayer shut down");
            }
        } catch (SQLException e) {
            logger.warning("Error closing database connection: " + e.getMessage());
        }
    }
    
    /**
     * Store simulation configuration.
     * 
     * @param sessionId The ID of the session
     * @param config The configuration to store
     * @return true if storage was successful
     */
    public boolean storeConfig(String sessionId, Map<String, Object> config) {
        if (connection == null) {
            logger.warning("Database not initialized");
            return false;
        }
        
        try {
            // Convert config to JSON
            String configJson = serializeToJson(config);
            
            // Insert or update config
            String sql = "INSERT OR REPLACE INTO configs (session_id, config, created_at) VALUES (?, ?, datetime('now'))";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, sessionId);
                stmt.setString(2, configJson);
                stmt.executeUpdate();
            }
            
            logger.info("Stored configuration for session " + sessionId);
            return true;
        } catch (SQLException e) {
            logger.severe("Error storing configuration: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Retrieve simulation configuration.
     * 
     * @param sessionId The ID of the session
     * @return The configuration or null if not found
     */
    public Map<String, Object> getConfig(String sessionId) {
        if (connection == null) {
            logger.warning("Database not initialized");
            return null;
        }
        
        try {
            String sql = "SELECT config FROM configs WHERE session_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, sessionId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String configJson = rs.getString("config");
                        return deserializeFromJson(configJson);
                    }
                }
            }
            
            logger.warning("Configuration not found for session " + sessionId);
            return null;
        } catch (SQLException e) {
            logger.severe("Error retrieving configuration: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Store simulation results.
     * 
     * @param sessionId The ID of the session
     * @param nodeId The ID of the node
     * @param results The results to store
     * @return true if storage was successful
     */
    public boolean storeResults(String sessionId, String nodeId, Map<String, Object> results) {
        if (connection == null) {
            logger.warning("Database not initialized");
            return false;
        }
        
        try {
            // Convert results to JSON
            String resultsJson = serializeToJson(results);
            
            // Insert results
            String sql = "INSERT INTO results (session_id, node_id, results, timestamp) VALUES (?, ?, ?, datetime('now'))";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, sessionId);
                stmt.setString(2, nodeId);
                stmt.setString(3, resultsJson);
                stmt.executeUpdate();
            }
            
            logger.fine("Stored results for session " + sessionId + ", node " + nodeId);
            return true;
        } catch (SQLException e) {
            logger.severe("Error storing results: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Store final results for a session.
     * 
     * @param sessionId The ID of the session
     * @param results The final results to store
     * @return true if storage was successful
     */
    public boolean storeFinalResults(String sessionId, Map<String, Object> results) {
        if (connection == null) {
            logger.warning("Database not initialized");
            return false;
        }
        
        try {
            // Convert results to JSON
            String resultsJson = serializeToJson(results);
            
            // Insert final results
            String sql = "INSERT OR REPLACE INTO final_results (session_id, results, timestamp) VALUES (?, ?, datetime('now'))";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, sessionId);
                stmt.setString(2, resultsJson);
                stmt.executeUpdate();
            }
            
            logger.info("Stored final results for session " + sessionId);
            return true;
        } catch (SQLException e) {
            logger.severe("Error storing final results: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Retrieve final results for a session.
     * 
     * @param sessionId The ID of the session
     * @return The final results or null if not found
     */
    public Map<String, Object> getFinalResults(String sessionId) {
        if (connection == null) {
            logger.warning("Database not initialized");
            return null;
        }
        
        try {
            String sql = "SELECT results FROM final_results WHERE session_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, sessionId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String resultsJson = rs.getString("results");
                        return deserializeFromJson(resultsJson);
                    }
                }
            }
            
            logger.warning("Final results not found for session " + sessionId);
            return null;
        } catch (SQLException e) {
            logger.severe("Error retrieving final results: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Retrieve results for a session.
     * 
     * @param sessionId The ID of the session
     * @return List of result entries
     */
    public List<Map<String, Object>> getResults(String sessionId) {
        if (connection == null) {
            logger.warning("Database not initialized");
            return new ArrayList<>();
        }
        
        try {
            String sql = "SELECT node_id, results, timestamp FROM results WHERE session_id = ? ORDER BY timestamp";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, sessionId);
                
                List<Map<String, Object>> resultsList = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String nodeId = rs.getString("node_id");
                        String resultsJson = rs.getString("results");
                        String timestamp = rs.getString("timestamp");
                        
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("nodeId", nodeId);
                        entry.put("timestamp", timestamp);
                        entry.put("results", deserializeFromJson(resultsJson));
                        
                        resultsList.add(entry);
                    }
                }
                
                return resultsList;
            }
        } catch (SQLException e) {
            logger.severe("Error retrieving results: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Store a log entry.
     * 
     * @param sessionId The ID of the session (optional, can be null)
     * @param nodeId The ID of the node (optional, can be null)
     * @param level The log level
     * @param message The log message
     * @return true if storage was successful
     */
    public boolean storeLog(String sessionId, String nodeId, String level, String message) {
        if (connection == null) {
            logger.warning("Database not initialized");
            return false;
        }
        
        try {
            String sql = "INSERT INTO logs (session_id, node_id, level, message, timestamp) VALUES (?, ?, ?, ?, datetime('now'))";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, sessionId);
                stmt.setString(2, nodeId);
                stmt.setString(3, level);
                stmt.setString(4, message);
                stmt.executeUpdate();
            }
            
            return true;
        } catch (SQLException e) {
            logger.severe("Error storing log: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Retrieve logs for a session.
     * 
     * @param sessionId The ID of the session
     * @return List of log entries
     */
    public List<Map<String, String>> getLogs(String sessionId) {
        if (connection == null) {
            logger.warning("Database not initialized");
            return new ArrayList<>();
        }
        
        try {
            String sql = "SELECT node_id, level, message, timestamp FROM logs WHERE session_id = ? ORDER BY timestamp";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, sessionId);
                
                List<Map<String, String>> logs = new ArrayList<>();
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> log = new HashMap<>();
                        log.put("nodeId", rs.getString("node_id"));
                        log.put("level", rs.getString("level"));
                        log.put("message", rs.getString("message"));
                        log.put("timestamp", rs.getString("timestamp"));
                        logs.add(log);
                    }
                }
                
                return logs;
            }
        } catch (SQLException e) {
            logger.severe("Error retrieving logs: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Create database tables if they don't exist.
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Create configs table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS configs (" +
                "session_id TEXT PRIMARY KEY, " +
                "config TEXT NOT NULL, " +
                "created_at TEXT NOT NULL" +
                ")"
            );
            
            // Create results table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS results (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "session_id TEXT NOT NULL, " +
                "node_id TEXT NOT NULL, " +
                "results TEXT NOT NULL, " +
                "timestamp TEXT NOT NULL" +
                ")"
            );
            
            // Create final_results table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS final_results (" +
                "session_id TEXT PRIMARY KEY, " +
                "results TEXT NOT NULL, " +
                "timestamp TEXT NOT NULL" +
                ")"
            );
            
            // Create logs table
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS logs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "session_id TEXT, " +
                "node_id TEXT, " +
                "level TEXT NOT NULL, " +
                "message TEXT NOT NULL, " +
                "timestamp TEXT NOT NULL" +
                ")"
            );
            
            // Create indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_results_session ON results (session_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_logs_session ON logs (session_id)");
        }
    }
    
    /**
     * Serialize a map to JSON string.
     * 
     * @param map The map to serialize
     * @return JSON string
     */
    private String serializeToJson(Map<String, Object> map) {
        // In a real implementation, this would use a JSON library
        // For simplicity, we'll create a basic JSON representation
        StringBuilder json = new StringBuilder("{");
        
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;
            
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value == null) {
                json.append("null");
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value instanceof Boolean) {
                json.append(value);
            } else if (value instanceof Map) {
                json.append(serializeToJson((Map<String, Object>) value));
            } else if (value instanceof List) {
                json.append(serializeToJsonArray((List<Object>) value));
            } else {
                json.append("\"").append(value).append("\"");
            }
        }
        
        json.append("}");
        return json.toString();
    }
    
    /**
     * Serialize a list to JSON array string.
     * 
     * @param list The list to serialize
     * @return JSON array string
     */
    private String serializeToJsonArray(List<Object> list) {
        StringBuilder json = new StringBuilder("[");
        
        boolean first = true;
        for (Object value : list) {
            if (!first) {
                json.append(",");
            }
            first = false;
            
            if (value == null) {
                json.append("null");
            } else if (value instanceof Number) {
                json.append(value);
            } else if (value instanceof Boolean) {
                json.append(value);
            } else if (value instanceof Map) {
                json.append(serializeToJson((Map<String, Object>) value));
            } else if (value instanceof List) {
                json.append(serializeToJsonArray((List<Object>) value));
            } else {
                json.append("\"").append(value).append("\"");
            }
        }
        
        json.append("]");
        return json.toString();
    }
    
    /**
     * Deserialize a JSON string to a map.
     * 
     * @param json The JSON string
     * @return Deserialized map
     */
    private Map<String, Object> deserializeFromJson(String json) {
        // In a real implementation, this would use a JSON library
        // For simplicity, we'll return an empty map
        return new HashMap<>();
    }
}