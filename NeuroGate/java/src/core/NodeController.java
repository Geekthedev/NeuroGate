package core;

import interop.NeuroBridge;
import net.NodeComm;
import simulation.ResultProcessor;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * NodeController coordinates simulation sessions across nodes.
 * It manages the lifecycle of nodes and distributes simulation tasks.
 */
public class NodeController {
    private static final Logger logger = Logger.getLogger(NodeController.class.getName());
    
    private final Map<String, NodeInfo> nodes;
    private final Map<String, SimulationSession> sessions;
    private final TaskScheduler scheduler;
    private final ResultProcessor resultProcessor;
    private final NeuroBridge localBridge;
    
    public NodeController(TaskScheduler scheduler, ResultProcessor resultProcessor) {
        this.nodes = new ConcurrentHashMap<>();
        this.sessions = new ConcurrentHashMap<>();
        this.scheduler = scheduler;
        this.resultProcessor = resultProcessor;
        this.localBridge = new NeuroBridge();
        
        try {
            this.localBridge.initialize();
            logger.info("Local NeuroCore bridge initialized successfully");
        } catch (Exception e) {
            logger.severe("Failed to initialize local NeuroCore bridge: " + e.getMessage());
            throw new RuntimeException("Failed to initialize NodeController", e);
        }
    }
    
    /**
     * Register a new node in the network.
     * 
     * @param nodeId Unique identifier for the node
     * @param address Network address of the node
     * @param port Communication port for the node
     * @param capabilities Map of capability name to capacity (e.g. "neurons": 1000)
     * @return true if registration was successful
     */
    public boolean registerNode(String nodeId, String address, int port, Map<String, Integer> capabilities) {
        if (nodes.containsKey(nodeId)) {
            logger.warning("Node with ID " + nodeId + " already registered");
            return false;
        }
        
        NodeInfo node = new NodeInfo(nodeId, address, port, capabilities);
        nodes.put(nodeId, node);
        
        logger.info("Registered node " + nodeId + " at " + address + ":" + port);
        return true;
    }
    
    /**
     * Unregister a node from the network.
     * 
     * @param nodeId The ID of the node to unregister
     * @return true if unregistration was successful
     */
    public boolean unregisterNode(String nodeId) {
        if (!nodes.containsKey(nodeId)) {
            logger.warning("Node with ID " + nodeId + " not found");
            return false;
        }
        
        // Handle any active sessions on this node
        for (SimulationSession session : sessions.values()) {
            if (session.getNodes().contains(nodeId)) {
                // Either migrate or terminate affected sessions
                logger.warning("Session " + session.getId() + " affected by node " + nodeId + " departure");
                session.removeNode(nodeId);
                
                if (session.getNodes().isEmpty()) {
                    // No nodes left, terminate session
                    terminateSession(session.getId());
                }
            }
        }
        
        nodes.remove(nodeId);
        logger.info("Unregistered node " + nodeId);
        return true;
    }
    
    /**
     * Create a new simulation session.
     * 
     * @param config Configuration for the simulation
     * @return Session ID if creation was successful, null otherwise
     */
    public String createSession(SimulationConfig config) {
        String sessionId = UUID.randomUUID().toString();
        
        // Select nodes for this session based on requirements
        List<String> selectedNodes = selectNodesForSession(config);
        if (selectedNodes.isEmpty()) {
            logger.warning("No suitable nodes found for simulation");
            return null;
        }
        
        SimulationSession session = new SimulationSession(sessionId, config, selectedNodes);
        sessions.put(sessionId, session);
        
        // Initialize nodes for this session
        for (String nodeId : selectedNodes) {
            NodeInfo node = nodes.get(nodeId);
            try {
                NodeComm comm = new NodeComm(node.getAddress(), node.getPort());
                boolean initialized = comm.initializeSession(sessionId, config);
                if (!initialized) {
                    logger.warning("Failed to initialize node " + nodeId + " for session " + sessionId);
                    session.removeNode(nodeId);
                }
            } catch (Exception e) {
                logger.severe("Error initializing node " + nodeId + ": " + e.getMessage());
                session.removeNode(nodeId);
            }
        }
        
        // Check if we still have nodes for this session
        if (session.getNodes().isEmpty()) {
            logger.severe("All nodes failed to initialize for session " + sessionId);
            sessions.remove(sessionId);
            return null;
        }
        
        // Schedule regular monitoring of this session
        scheduler.scheduleSessionMonitoring(sessionId);
        
        logger.info("Created simulation session " + sessionId + " with " + session.getNodes().size() + " nodes");
        return sessionId;
    }
    
    /**
     * Start a simulation session.
     * 
     * @param sessionId The ID of the session to start
     * @return true if session was started successfully
     */
    public boolean startSession(String sessionId) {
        SimulationSession session = sessions.get(sessionId);
        if (session == null) {
            logger.warning("Session " + sessionId + " not found");
            return false;
        }
        
        if (session.isRunning()) {
            logger.warning("Session " + sessionId + " already running");
            return false;
        }
        
        // Start the simulation on all nodes
        boolean allStarted = true;
        for (String nodeId : session.getNodes()) {
            NodeInfo node = nodes.get(nodeId);
            try {
                NodeComm comm = new NodeComm(node.getAddress(), node.getPort());
                boolean started = comm.startSimulation(sessionId);
                if (!started) {
                    logger.warning("Failed to start simulation on node " + nodeId);
                    allStarted = false;
                }
            } catch (Exception e) {
                logger.severe("Error starting simulation on node " + nodeId + ": " + e.getMessage());
                allStarted = false;
            }
        }
        
        if (!allStarted) {
            logger.warning("Some nodes failed to start for session " + sessionId);
            return false;
        }
        
        session.setRunning(true);
        
        // Schedule regular result collection
        scheduler.scheduleResultCollection(sessionId);
        
        logger.info("Started simulation session " + sessionId);
        return true;
    }
    
    /**
     * Pause a running simulation session.
     * 
     * @param sessionId The ID of the session to pause
     * @return true if session was paused successfully
     */
    public boolean pauseSession(String sessionId) {
        SimulationSession session = sessions.get(sessionId);
        if (session == null) {
            logger.warning("Session " + sessionId + " not found");
            return false;
        }
        
        if (!session.isRunning()) {
            logger.warning("Session " + sessionId + " not running");
            return false;
        }
        
        // Pause the simulation on all nodes
        boolean allPaused = true;
        for (String nodeId : session.getNodes()) {
            NodeInfo node = nodes.get(nodeId);
            try {
                NodeComm comm = new NodeComm(node.getAddress(), node.getPort());
                boolean paused = comm.pauseSimulation(sessionId);
                if (!paused) {
                    logger.warning("Failed to pause simulation on node " + nodeId);
                    allPaused = false;
                }
            } catch (Exception e) {
                logger.severe("Error pausing simulation on node " + nodeId + ": " + e.getMessage());
                allPaused = false;
            }
        }
        
        if (!allPaused) {
            logger.warning("Some nodes failed to pause for session " + sessionId);
            return false;
        }
        
        session.setRunning(false);
        logger.info("Paused simulation session " + sessionId);
        return true;
    }
    
    /**
     * Terminate a simulation session and clean up resources.
     * 
     * @param sessionId The ID of the session to terminate
     * @return true if session was terminated successfully
     */
    public boolean terminateSession(String sessionId) {
        SimulationSession session = sessions.get(sessionId);
        if (session == null) {
            logger.warning("Session " + sessionId + " not found");
            return false;
        }
        
        // Stop scheduled tasks for this session
        scheduler.cancelSessionTasks(sessionId);
        
        // Terminate the simulation on all nodes
        for (String nodeId : session.getNodes()) {
            NodeInfo node = nodes.get(nodeId);
            try {
                NodeComm comm = new NodeComm(node.getAddress(), node.getPort());
                boolean terminated = comm.terminateSimulation(sessionId);
                if (!terminated) {
                    logger.warning("Failed to terminate simulation on node " + nodeId);
                }
            } catch (Exception e) {
                logger.severe("Error terminating simulation on node " + nodeId + ": " + e.getMessage());
            }
        }
        
        // Process final results
        resultProcessor.processFinalResults(sessionId);
        
        sessions.remove(sessionId);
        logger.info("Terminated simulation session " + sessionId);
        return true;
    }
    
    /**
     * Get the status of a simulation session.
     * 
     * @param sessionId The ID of the session
     * @return A map containing session status information
     */
    public Map<String, Object> getSessionStatus(String sessionId) {
        SimulationSession session = sessions.get(sessionId);
        if (session == null) {
            logger.warning("Session " + sessionId + " not found");
            return null;
        }
        
        Map<String, Object> status = new HashMap<>();
        status.put("id", session.getId());
        status.put("running", session.isRunning());
        status.put("nodeCount", session.getNodes().size());
        status.put("nodes", session.getNodes());
        status.put("startTime", session.getStartTime());
        status.put("stepCount", session.getStepCount());
        
        // Collect node-specific status
        Map<String, Map<String, Object>> nodeStatus = new HashMap<>();
        for (String nodeId : session.getNodes()) {
            NodeInfo node = nodes.get(nodeId);
            try {
                NodeComm comm = new NodeComm(node.getAddress(), node.getPort());
                Map<String, Object> ns = comm.getStatus(sessionId);
                nodeStatus.put(nodeId, ns);
            } catch (Exception e) {
                logger.warning("Error getting status from node " + nodeId + ": " + e.getMessage());
                nodeStatus.put(nodeId, Map.of("error", e.getMessage()));
            }
        }
        status.put("nodeStatus", nodeStatus);
        
        return status;
    }
    
    /**
     * Collect results from all nodes in a session.
     * 
     * @param sessionId The ID of the session
     * @return true if results were collected successfully
     */
    public boolean collectResults(String sessionId) {
        SimulationSession session = sessions.get(sessionId);
        if (session == null) {
            logger.warning("Session " + sessionId + " not found");
            return false;
        }
        
        // Collect results from all nodes
        for (String nodeId : session.getNodes()) {
            NodeInfo node = nodes.get(nodeId);
            try {
                NodeComm comm = new NodeComm(node.getAddress(), node.getPort());
                Map<String, Object> results = comm.getResults(sessionId);
                if (results != null) {
                    resultProcessor.processResults(sessionId, nodeId, results);
                }
            } catch (Exception e) {
                logger.warning("Error collecting results from node " + nodeId + ": " + e.getMessage());
            }
        }
        
        session.incrementStepCount();
        return true;
    }
    
    /**
     * Get all registered nodes.
     * 
     * @return Map of node IDs to node information
     */
    public Map<String, NodeInfo> getNodes() {
        return new HashMap<>(nodes);
    }
    
    /**
     * Get all active sessions.
     * 
     * @return Map of session IDs to session information
     */
    public Map<String, SimulationSession> getSessions() {
        return new HashMap<>(sessions);
    }
    
    /**
     * Shut down the controller and clean up resources.
     */
    public void shutdown() {
        // Terminate all sessions
        for (String sessionId : new ArrayList<>(sessions.keySet())) {
            terminateSession(sessionId);
        }
        
        // Clean up local NeuroCore bridge
        localBridge.shutdown();
        
        logger.info("NodeController shut down");
    }
    
    /**
     * Select appropriate nodes for a simulation session based on requirements.
     * 
     * @param config The simulation configuration
     * @return List of selected node IDs
     */
    private List<String> selectNodesForSession(SimulationConfig config) {
        List<String> selectedNodes = new ArrayList<>();
        
        // Simple selection strategy: find nodes with enough capacity
        int requiredNeurons = config.getNeuronCount();
        int neuronCapacity = 0;
        
        // Sort nodes by capacity
        List<NodeInfo> sortedNodes = new ArrayList<>(nodes.values());
        sortedNodes.sort((a, b) -> {
            int capA = a.getCapabilities().getOrDefault("neurons", 0);
            int capB = b.getCapabilities().getOrDefault("neurons", 0);
            return Integer.compare(capB, capA);  // Descending order
        });
        
        // Select nodes until we have enough capacity
        for (NodeInfo node : sortedNodes) {
            int capacity = node.getCapabilities().getOrDefault("neurons", 0);
            if (capacity > 0) {
                selectedNodes.add(node.getId());
                neuronCapacity += capacity;
                
                if (neuronCapacity >= requiredNeurons) {
                    break;  // We have enough capacity
                }
            }
        }
        
        // If we don't have enough nodes, use local node as well
        if (neuronCapacity < requiredNeurons) {
            selectedNodes.add("local");
        }
        
        return selectedNodes;
    }
    
    /**
     * Information about a node in the network.
     */
    public static class NodeInfo {
        private final String id;
        private final String address;
        private final int port;
        private final Map<String, Integer> capabilities;
        
        public NodeInfo(String id, String address, int port, Map<String, Integer> capabilities) {
            this.id = id;
            this.address = address;
            this.port = port;
            this.capabilities = capabilities;
        }
        
        public String getId() {
            return id;
        }
        
        public String getAddress() {
            return address;
        }
        
        public int getPort() {
            return port;
        }
        
        public Map<String, Integer> getCapabilities() {
            return capabilities;
        }
    }
    
    /**
     * Configuration for a simulation session.
     */
    public static class SimulationConfig {
        private final int neuronCount;
        private final int synapseCount;
        private final String topology;
        private final Map<String, Object> parameters;
        
        public SimulationConfig(int neuronCount, int synapseCount, String topology, Map<String, Object> parameters) {
            this.neuronCount = neuronCount;
            this.synapseCount = synapseCount;
            this.topology = topology;
            this.parameters = parameters;
        }
        
        public int getNeuronCount() {
            return neuronCount;
        }
        
        public int getSynapseCount() {
            return synapseCount;
        }
        
        public String getTopology() {
            return topology;
        }
        
        public Map<String, Object> getParameters() {
            return parameters;
        }
    }
    
    /**
     * Information about a simulation session.
     */
    public static class SimulationSession {
        private final String id;
        private final SimulationConfig config;
        private final List<String> nodes;
        private final long startTime;
        private boolean running;
        private long stepCount;
        
        public SimulationSession(String id, SimulationConfig config, List<String> nodes) {
            this.id = id;
            this.config = config;
            this.nodes = new ArrayList<>(nodes);
            this.startTime = System.currentTimeMillis();
            this.running = false;
            this.stepCount = 0;
        }
        
        public String getId() {
            return id;
        }
        
        public SimulationConfig getConfig() {
            return config;
        }
        
        public List<String> getNodes() {
            return new ArrayList<>(nodes);
        }
        
        public void removeNode(String nodeId) {
            nodes.remove(nodeId);
        }
        
        public long getStartTime() {
            return startTime;
        }
        
        public boolean isRunning() {
            return running;
        }
        
        public void setRunning(boolean running) {
            this.running = running;
        }
        
        public long getStepCount() {
            return stepCount;
        }
        
        public void incrementStepCount() {
            this.stepCount++;
        }
    }
}