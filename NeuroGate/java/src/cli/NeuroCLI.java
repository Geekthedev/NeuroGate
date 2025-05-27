package cli;

import core.NodeController;
import core.TaskScheduler;
import db.PersistenceLayer;
import interop.NeuroBridge;
import simulation.ResultProcessor;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * NeuroCLI provides a command-line interface for controlling simulations.
 */
public class NeuroCLI {
    private static final Logger logger = Logger.getLogger(NeuroCLI.class.getName());
    
    private final NodeController nodeController;
    private final PersistenceLayer persistenceLayer;
    private final BufferedReader reader;
    private boolean running;
    
    /**
     * Create a new CLI instance.
     * 
     * @param nodeController The node controller
     * @param persistenceLayer The persistence layer
     */
    public NeuroCLI(NodeController nodeController, PersistenceLayer persistenceLayer) {
        this.nodeController = nodeController;
        this.persistenceLayer = persistenceLayer;
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.running = false;
    }
    
    /**
     * Start the CLI and begin processing commands.
     */
    public void start() {
        running = true;
        
        printWelcome();
        
        while (running) {
            try {
                System.out.print("neurogate> ");
                String command = reader.readLine().trim();
                
                if (command.isEmpty()) {
                    continue;
                }
                
                processCommand(command);
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                logger.severe("Error processing command: " + e.getMessage());
            }
        }
    }
    
    /**
     * Stop the CLI.
     */
    public void stop() {
        running = false;
    }
    
    /**
     * Print the welcome message.
     */
    private void printWelcome() {
        System.out.println("===================================");
        System.out.println("  NeuroGate CLI - Version 0.1.0");
        System.out.println("===================================");
        System.out.println("Type 'help' for a list of commands.");
        System.out.println();
    }
    
    /**
     * Process a command.
     * 
     * @param command The command to process
     */
    private void processCommand(String command) {
        String[] parts = command.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";
        
        switch (cmd) {
            case "help":
                printHelp();
                break;
                
            case "exit":
            case "quit":
                System.out.println("Exiting NeuroGate CLI...");
                stop();
                break;
                
            case "node":
                processNodeCommand(args);
                break;
                
            case "session":
                processSessionCommand(args);
                break;
                
            case "result":
                processResultCommand(args);
                break;
                
            case "log":
                processLogCommand(args);
                break;
                
            case "local":
                processLocalCommand(args);
                break;
                
            default:
                System.out.println("Unknown command: " + cmd);
                System.out.println("Type 'help' for a list of commands.");
                break;
        }
    }
    
    /**
     * Print help information.
     */
    private void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  help                       - Show this help information");
        System.out.println("  exit, quit                 - Exit the CLI");
        System.out.println();
        System.out.println("Node commands:");
        System.out.println("  node list                  - List all registered nodes");
        System.out.println("  node register <id> <addr> <port> - Register a new node");
        System.out.println("  node unregister <id>       - Unregister a node");
        System.out.println();
        System.out.println("Session commands:");
        System.out.println("  session list               - List all active sessions");
        System.out.println("  session create <neurons> <synapses> - Create a new session");
        System.out.println("  session start <id>         - Start a session");
        System.out.println("  session pause <id>         - Pause a running session");
        System.out.println("  session terminate <id>     - Terminate a session");
        System.out.println("  session status <id>        - Get session status");
        System.out.println();
        System.out.println("Result commands:");
        System.out.println("  result get <sessionId>     - Get results for a session");
        System.out.println();
        System.out.println("Log commands:");
        System.out.println("  log get <sessionId>        - Get logs for a session");
        System.out.println();
        System.out.println("Local commands:");
        System.out.println("  local status               - Get local node status");
        System.out.println("  local test                 - Run a simple local test");
    }
    
    /**
     * Process a node command.
     * 
     * @param args The command arguments
     */
    private void processNodeCommand(String args) {
        String[] parts = args.split("\\s+");
        String subCmd = parts.length > 0 ? parts[0].toLowerCase() : "";
        
        switch (subCmd) {
            case "list":
                // List all registered nodes
                Map<String, NodeController.NodeInfo> nodes = nodeController.getNodes();
                if (nodes.isEmpty()) {
                    System.out.println("No nodes registered.");
                } else {
                    System.out.println("Registered nodes:");
                    for (NodeController.NodeInfo node : nodes.values()) {
                        System.out.println("  " + node.getId() + " - " + node.getAddress() + ":" + node.getPort());
                        System.out.println("    Capabilities: " + node.getCapabilities());
                    }
                }
                break;
                
            case "register":
                // Register a new node
                if (parts.length < 4) {
                    System.out.println("Usage: node register <id> <address> <port>");
                    break;
                }
                
                String nodeId = parts[1];
                String address = parts[2];
                int port;
                try {
                    port = Integer.parseInt(parts[3]);
                } catch (NumberFormatException e) {
                    System.out.println("Error: Port must be a number");
                    break;
                }
                
                // Default capabilities
                Map<String, Integer> capabilities = new HashMap<>();
                capabilities.put("neurons", 1000);
                capabilities.put("synapses", 5000);
                
                boolean registered = nodeController.registerNode(nodeId, address, port, capabilities);
                if (registered) {
                    System.out.println("Node " + nodeId + " registered successfully.");
                } else {
                    System.out.println("Failed to register node " + nodeId);
                }
                break;
                
            case "unregister":
                // Unregister a node
                if (parts.length < 2) {
                    System.out.println("Usage: node unregister <id>");
                    break;
                }
                
                nodeId = parts[1];
                boolean unregistered = nodeController.unregisterNode(nodeId);
                if (unregistered) {
                    System.out.println("Node " + nodeId + " unregistered successfully.");
                } else {
                    System.out.println("Failed to unregister node " + nodeId);
                }
                break;
                
            default:
                System.out.println("Unknown node command: " + subCmd);
                System.out.println("Type 'help' for a list of commands.");
                break;
        }
    }
    
    /**
     * Process a session command.
     * 
     * @param args The command arguments
     */
    private void processSessionCommand(String args) {
        String[] parts = args.split("\\s+");
        String subCmd = parts.length > 0 ? parts[0].toLowerCase() : "";
        
        switch (subCmd) {
            case "list":
                // List all active sessions
                Map<String, NodeController.SimulationSession> sessions = nodeController.getSessions();
                if (sessions.isEmpty()) {
                    System.out.println("No active sessions.");
                } else {
                    System.out.println("Active sessions:");
                    for (NodeController.SimulationSession session : sessions.values()) {
                        System.out.println("  " + session.getId() + " - Running: " + session.isRunning());
                        System.out.println("    Nodes: " + session.getNodes());
                        System.out.println("    Started: " + new java.util.Date(session.getStartTime()));
                        System.out.println("    Steps: " + session.getStepCount());
                    }
                }
                break;
                
            case "create":
                // Create a new session
                if (parts.length < 3) {
                    System.out.println("Usage: session create <neurons> <synapses>");
                    break;
                }
                
                int neurons, synapses;
                try {
                    neurons = Integer.parseInt(parts[1]);
                    synapses = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    System.out.println("Error: Neuron and synapse counts must be numbers");
                    break;
                }
                
                // Create configuration
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("timeStep", 1.0);
                parameters.put("threshold", -55.0);
                parameters.put("restPotential", -70.0);
                parameters.put("refractoryPeriod", 2.0);
                
                NodeController.SimulationConfig config = new NodeController.SimulationConfig(
                    neurons, synapses, "random", parameters
                );
                
                String sessionId = nodeController.createSession(config);
                if (sessionId != null) {
                    System.out.println("Session created with ID: " + sessionId);
                    
                    // Store configuration
                    Map<String, Object> configMap = new HashMap<>();
                    configMap.put("neurons", neurons);
                    configMap.put("synapses", synapses);
                    configMap.put("topology", "random");
                    configMap.put("parameters", parameters);
                    
                    persistenceLayer.storeConfig(sessionId, configMap);
                } else {
                    System.out.println("Failed to create session");
                }
                break;
                
            case "start":
                // Start a session
                if (parts.length < 2) {
                    System.out.println("Usage: session start <id>");
                    break;
                }
                
                sessionId = parts[1];
                boolean started = nodeController.startSession(sessionId);
                if (started) {
                    System.out.println("Session " + sessionId + " started successfully.");
                } else {
                    System.out.println("Failed to start session " + sessionId);
                }
                break;
                
            case "pause":
                // Pause a session
                if (parts.length < 2) {
                    System.out.println("Usage: session pause <id>");
                    break;
                }
                
                sessionId = parts[1];
                boolean paused = nodeController.pauseSession(sessionId);
                if (paused) {
                    System.out.println("Session " + sessionId + " paused successfully.");
                } else {
                    System.out.println("Failed to pause session " + sessionId);
                }
                break;
                
            case "terminate":
                // Terminate a session
                if (parts.length < 2) {
                    System.out.println("Usage: session terminate <id>");
                    break;
                }
                
                sessionId = parts[1];
                boolean terminated = nodeController.terminateSession(sessionId);
                if (terminated) {
                    System.out.println("Session " + sessionId + " terminated successfully.");
                } else {
                    System.out.println("Failed to terminate session " + sessionId);
                }
                break;
                
            case "status":
                // Get session status
                if (parts.length < 2) {
                    System.out.println("Usage: session status <id>");
                    break;
                }
                
                sessionId = parts[1];
                Map<String, Object> status = nodeController.getSessionStatus(sessionId);
                if (status != null) {
                    System.out.println("Status for session " + sessionId + ":");
                    printMap(status, "  ");
                } else {
                    System.out.println("Failed to get status for session " + sessionId);
                }
                break;
                
            default:
                System.out.println("Unknown session command: " + subCmd);
                System.out.println("Type 'help' for a list of commands.");
                break;
        }
    }
    
    /**
     * Process a result command.
     * 
     * @param args The command arguments
     */
    private void processResultCommand(String args) {
        String[] parts = args.split("\\s+");
        String subCmd = parts.length > 0 ? parts[0].toLowerCase() : "";
        
        switch (subCmd) {
            case "get":
                // Get results for a session
                if (parts.length < 2) {
                    System.out.println("Usage: result get <sessionId>");
                    break;
                }
                
                String sessionId = parts[1];
                Map<String, Object> results = persistenceLayer.getFinalResults(sessionId);
                if (results != null) {
                    System.out.println("Final results for session " + sessionId + ":");
                    printMap(results, "  ");
                } else {
                    System.out.println("No final results found for session " + sessionId);
                    
                    // Try to get individual results
                    List<Map<String, Object>> resultsList = persistenceLayer.getResults(sessionId);
                    if (!resultsList.isEmpty()) {
                        System.out.println("Individual results for session " + sessionId + ":");
                        for (Map<String, Object> result : resultsList) {
                            System.out.println("  Node: " + result.get("nodeId") + ", Time: " + result.get("timestamp"));
                            printMap((Map<String, Object>) result.get("results"), "    ");
                            System.out.println();
                        }
                    } else {
                        System.out.println("No results found for session " + sessionId);
                    }
                }
                break;
                
            default:
                System.out.println("Unknown result command: " + subCmd);
                System.out.println("Type 'help' for a list of commands.");
                break;
        }
    }
    
    /**
     * Process a log command.
     * 
     * @param args The command arguments
     */
    private void processLogCommand(String args) {
        String[] parts = args.split("\\s+");
        String subCmd = parts.length > 0 ? parts[0].toLowerCase() : "";
        
        switch (subCmd) {
            case "get":
                // Get logs for a session
                if (parts.length < 2) {
                    System.out.println("Usage: log get <sessionId>");
                    break;
                }
                
                String sessionId = parts[1];
                List<Map<String, String>> logs = persistenceLayer.getLogs(sessionId);
                if (!logs.isEmpty()) {
                    System.out.println("Logs for session " + sessionId + ":");
                    for (Map<String, String> log : logs) {
                        System.out.println("  " + log.get("timestamp") + " [" + log.get("level") + "] " +
                                          (log.get("nodeId") != null ? log.get("nodeId") + ": " : "") +
                                          log.get("message"));
                    }
                } else {
                    System.out.println("No logs found for session " + sessionId);
                }
                break;
                
            default:
                System.out.println("Unknown log command: " + subCmd);
                System.out.println("Type 'help' for a list of commands.");
                break;
        }
    }
    
    /**
     * Process a local command.
     * 
     * @param args The command arguments
     */
    private void processLocalCommand(String args) {
        String[] parts = args.split("\\s+");
        String subCmd = parts.length > 0 ? parts[0].toLowerCase() : "";
        
        switch (subCmd) {
            case "status":
                // Get local node status
                System.out.println("Local node status:");
                System.out.println("  Runtime: Java " + System.getProperty("java.version"));
                System.out.println("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
                System.out.println("  Memory: " + (Runtime.getRuntime().totalMemory() / 1024 / 1024) + " MB total, " +
                                 (Runtime.getRuntime().freeMemory() / 1024 / 1024) + " MB free");
                break;
                
            case "test":
                // Run a simple local test
                System.out.println("Running local neuron test...");
                
                try {
                    NeuroBridge bridge = new NeuroBridge();
                    bridge.initialize();
                    
                    // Create some neurons
                    NeuroBridge.NeuronHandle n1 = bridge.createNeuron(1, NeuroBridge.NeuronType.EXCITATORY, 
                                                                    NeuroBridge.ActivationFunction.SIGMOID);
                    NeuroBridge.NeuronHandle n2 = bridge.createNeuron(2, NeuroBridge.NeuronType.EXCITATORY, 
                                                                    NeuroBridge.ActivationFunction.SIGMOID);
                    
                    // Connect neurons
                    bridge.connectNeurons(n1, n2);
                    
                    // Create a synapse
                    bridge.createSynapse(1, n1, n2, NeuroBridge.SynapseType.EXCITATORY);
                    
                    // Run simulation
                    System.out.println("Running simulation steps...");
                    float[] inputs = new float[] { 1.0f, 0.0f };
                    
                    for (int i = 0; i < 10; i++) {
                        float[] outputs = bridge.runSimulation(inputs, 1.0f);
                        
                        System.out.println("Step " + i + " outputs: [" + 
                                         outputs[0] + ", " + outputs[1] + "]");
                        
                        // Reduce input after a few steps
                        if (i == 5) {
                            inputs[0] = 0.5f;
                        }
                    }
                    
                    // Clean up
                    bridge.shutdown();
                    System.out.println("Test completed successfully.");
                } catch (Exception e) {
                    System.out.println("Test failed: " + e.getMessage());
                    e.printStackTrace();
                }
                break;
                
            default:
                System.out.println("Unknown local command: " + subCmd);
                System.out.println("Type 'help' for a list of commands.");
                break;
        }
    }
    
    /**
     * Print a map with indentation.
     * 
     * @param map The map to print
     * @param indent The indentation string
     */
    private void printMap(Map<String, Object> map, String indent) {
        if (map == null) {
            System.out.println(indent + "null");
            return;
        }
        
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof Map) {
                System.out.println(indent + key + ":");
                printMap((Map<String, Object>) value, indent + "  ");
            } else if (value instanceof List) {
                System.out.println(indent + key + ":");
                List<?> list = (List<?>) value;
                for (Object item : list) {
                    if (item instanceof Map) {
                        printMap((Map<String, Object>) item, indent + "  ");
                    } else {
                        System.out.println(indent + "  " + item);
                    }
                }
            } else {
                System.out.println(indent + key + ": " + value);
            }
        }
    }
}