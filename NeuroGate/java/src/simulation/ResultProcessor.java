package simulation;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import db.PersistenceLayer;

/**
 * ResultProcessor handles processing and storing simulation results.
 */
public class ResultProcessor {
    private static final Logger logger = Logger.getLogger(ResultProcessor.class.getName());
    
    private final PersistenceLayer persistenceLayer;
    private final Map<String, SessionResults> sessionResults;
    
    public ResultProcessor(PersistenceLayer persistenceLayer) {
        this.persistenceLayer = persistenceLayer;
        this.sessionResults = new ConcurrentHashMap<>();
    }
    
    /**
     * Process results from a node for a specific session.
     * 
     * @param sessionId The ID of the session
     * @param nodeId The ID of the node
     * @param results The results data
     */
    public void processResults(String sessionId, String nodeId, Map<String, Object> results) {
        if (sessionId == null || nodeId == null || results == null) {
            logger.warning("Invalid parameters for processResults");
            return;
        }
        
        // Get or create session results
        SessionResults sessionResult = sessionResults.computeIfAbsent(
            sessionId, id -> new SessionResults(id)
        );
        
        // Add node results
        sessionResult.addNodeResults(nodeId, results);
        
        // Store results in the persistence layer
        try {
            persistenceLayer.storeResults(sessionId, nodeId, results);
            logger.fine("Stored results for session " + sessionId + ", node " + nodeId);
        } catch (Exception e) {
            logger.warning("Failed to store results: " + e.getMessage());
        }
        
        // Process results (analyze, aggregate, etc.)
        processResultData(sessionId, results);
    }
    
    /**
     * Process final results when a session is terminated.
     * 
     * @param sessionId The ID of the session
     */
    public void processFinalResults(String sessionId) {
        if (sessionId == null) {
            logger.warning("Invalid session ID for processFinalResults");
            return;
        }
        
        SessionResults sessionResult = sessionResults.get(sessionId);
        if (sessionResult == null) {
            logger.warning("No results found for session " + sessionId);
            return;
        }
        
        // Generate final aggregated results
        Map<String, Object> finalResults = sessionResult.generateFinalResults();
        
        // Store final results
        try {
            persistenceLayer.storeFinalResults(sessionId, finalResults);
            logger.info("Stored final results for session " + sessionId);
        } catch (Exception e) {
            logger.warning("Failed to store final results: " + e.getMessage());
        }
        
        // Clean up session results
        sessionResults.remove(sessionId);
    }
    
    /**
     * Get results for a specific session.
     * 
     * @param sessionId The ID of the session
     * @return The results data or null if not found
     */
    public Map<String, Object> getSessionResults(String sessionId) {
        SessionResults sessionResult = sessionResults.get(sessionId);
        if (sessionResult == null) {
            logger.warning("No results found for session " + sessionId);
            return null;
        }
        
        return sessionResult.generateFinalResults();
    }
    
    /**
     * Process result data for analysis and aggregation.
     * 
     * @param sessionId The ID of the session
     * @param results The results data
     */
    private void processResultData(String sessionId, Map<String, Object> results) {
        // This is where you would implement result analysis, visualization preparation, etc.
        // For demonstration, we'll just log some basic metrics
        
        // Extract neuron states if available
        Map<Integer, Float> neuronStates = (Map<Integer, Float>) results.get("neuronStates");
        if (neuronStates != null) {
            float averageState = calculateAverageState(neuronStates);
            logger.fine("Session " + sessionId + " average neuron state: " + averageState);
        }
        
        // Extract synapse states if available
        Map<Integer, Float> synapseStates = (Map<Integer, Float>) results.get("synapseStates");
        if (synapseStates != null) {
            float averageWeight = calculateAverageState(synapseStates);
            logger.fine("Session " + sessionId + " average synapse weight: " + averageWeight);
        }
    }
    
    /**
     * Calculate the average value of a map of states.
     * 
     * @param states The map of states
     * @return The average value
     */
    private float calculateAverageState(Map<Integer, Float> states) {
        if (states == null || states.isEmpty()) {
            return 0.0f;
        }
        
        float sum = 0.0f;
        for (Float value : states.values()) {
            sum += value;
        }
        
        return sum / states.size();
    }
    
    /**
     * Class to hold results for a single simulation session.
     */
    private static class SessionResults {
        private final String sessionId;
        private final Map<String, Map<String, Object>> nodeResults;
        private long lastUpdateTime;
        
        public SessionResults(String sessionId) {
            this.sessionId = sessionId;
            this.nodeResults = new ConcurrentHashMap<>();
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        /**
         * Add results from a node.
         * 
         * @param nodeId The ID of the node
         * @param results The results data
         */
        public void addNodeResults(String nodeId, Map<String, Object> results) {
            nodeResults.put(nodeId, results);
            lastUpdateTime = System.currentTimeMillis();
        }
        
        /**
         * Generate final aggregated results for this session.
         * 
         * @return The aggregated results
         */
        public Map<String, Object> generateFinalResults() {
            Map<String, Object> finalResults = new HashMap<>();
            finalResults.put("sessionId", sessionId);
            finalResults.put("timestamp", lastUpdateTime);
            finalResults.put("nodeCount", nodeResults.size());
            
            // Aggregate neuron states across all nodes
            Map<Integer, Float> aggregatedNeuronStates = new HashMap<>();
            
            // Aggregate synapse states across all nodes
            Map<Integer, Float> aggregatedSynapseStates = new HashMap<>();
            
            for (Map<String, Object> nodeResult : nodeResults.values()) {
                // Process neuron states
                Map<Integer, Float> neuronStates = (Map<Integer, Float>) nodeResult.get("neuronStates");
                if (neuronStates != null) {
                    aggregatedNeuronStates.putAll(neuronStates);
                }
                
                // Process synapse states
                Map<Integer, Float> synapseStates = (Map<Integer, Float>) nodeResult.get("synapseStates");
                if (synapseStates != null) {
                    aggregatedSynapseStates.putAll(synapseStates);
                }
            }
            
            finalResults.put("neuronStates", aggregatedNeuronStates);
            finalResults.put("synapseStates", aggregatedSynapseStates);
            
            // Calculate summary metrics
            if (!aggregatedNeuronStates.isEmpty()) {
                float averageNeuronState = 0.0f;
                for (Float value : aggregatedNeuronStates.values()) {
                    averageNeuronState += value;
                }
                averageNeuronState /= aggregatedNeuronStates.size();
                finalResults.put("averageNeuronState", averageNeuronState);
            }
            
            if (!aggregatedSynapseStates.isEmpty()) {
                float averageSynapseWeight = 0.0f;
                for (Float value : aggregatedSynapseStates.values()) {
                    averageSynapseWeight += value;
                }
                averageSynapseWeight /= aggregatedSynapseStates.size();
                finalResults.put("averageSynapseWeight", averageSynapseWeight);
            }
            
            return finalResults;
        }
    }
}