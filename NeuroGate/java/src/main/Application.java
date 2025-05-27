package main;

import core.NodeController;
import core.TaskScheduler;
import db.PersistenceLayer;
import cli.NeuroCLI;
import security.AuthManager;
import simulation.ResultProcessor;
import java.util.logging.Logger;

/**
 * Main application class for NeuroGate.
 */
public class Application {
    private static final Logger logger = Logger.getLogger(Application.class.getName());
    
    private NodeController nodeController;
    private TaskScheduler taskScheduler;
    private PersistenceLayer persistenceLayer;
    private ResultProcessor resultProcessor;
    private AuthManager authManager;
    private NeuroCLI cli;
    
    /**
     * Initialize the application.
     * 
     * @return true if initialization was successful
     */
    public boolean initialize() {
        try {
            logger.info("Initializing NeuroGate application...");
            
            // Initialize persistence layer
            persistenceLayer = new PersistenceLayer();
            boolean persistenceInitialized = persistenceLayer.initialize();
            if (!persistenceInitialized) {
                logger.severe("Failed to initialize persistence layer");
                return false;
            }
            
            // Initialize result processor
            resultProcessor = new ResultProcessor(persistenceLayer);
            
            // Initialize node controller
            nodeController = new NodeController(null, resultProcessor);
            
            // Initialize task scheduler
            taskScheduler = new TaskScheduler(nodeController);
            
            // Set the task scheduler in the node controller
            // (This is a bit of a circular reference, but it's cleaner than alternatives)
            nodeController = new NodeController(taskScheduler, resultProcessor);
            
            // Initialize authentication manager
            authManager = new AuthManager();
            
            // Initialize CLI
            cli = new NeuroCLI(nodeController, persistenceLayer);
            
            logger.info("NeuroGate application initialized successfully");
            return true;
        } catch (Exception e) {
            logger.severe("Error initializing application: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Start the application.
     */
    public void start() {
        logger.info("Starting NeuroGate application...");
        
        // Start the CLI
        cli.start();
    }
    
    /**
     * Shutdown the application.
     */
    public void shutdown() {
        logger.info("Shutting down NeuroGate application...");
        
        // Stop the CLI
        if (cli != null) {
            cli.stop();
        }
        
        // Shutdown task scheduler
        if (taskScheduler != null) {
            taskScheduler.shutdown();
        }
        
        // Shutdown node controller
        if (nodeController != null) {
            nodeController.shutdown();
        }
        
        // Shutdown persistence layer
        if (persistenceLayer != null) {
            persistenceLayer.shutdown();
        }
        
        logger.info("NeuroGate application shutdown complete");
    }
    
    /**
     * Main entry point for the application.
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        Application app = new Application();
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutdown signal received, terminating...");
            app.shutdown();
        }));
        
        // Initialize and start application
        boolean initialized = app.initialize();
        if (initialized) {
            app.start();
        } else {
            System.err.println("Failed to initialize application");
            System.exit(1);
        }
    }
}