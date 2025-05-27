package core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * TaskScheduler manages scheduled tasks for simulation monitoring and result collection.
 */
public class TaskScheduler {
    private static final Logger logger = Logger.getLogger(TaskScheduler.class.getName());
    
    private final ScheduledExecutorService executor;
    private final Map<String, Map<TaskType, ScheduledFuture<?>>> scheduledTasks;
    private final NodeController nodeController;
    
    // Default intervals (in milliseconds)
    private static final long DEFAULT_MONITORING_INTERVAL = 5000;
    private static final long DEFAULT_RESULT_INTERVAL = 1000;
    
    // Task types
    public enum TaskType {
        MONITORING,
        RESULT_COLLECTION
    }
    
    public TaskScheduler(NodeController nodeController) {
        this.executor = Executors.newScheduledThreadPool(4);
        this.scheduledTasks = new ConcurrentHashMap<>();
        this.nodeController = nodeController;
    }
    
    /**
     * Schedule regular monitoring for a simulation session.
     * 
     * @param sessionId The ID of the session to monitor
     * @return true if scheduling was successful
     */
    public boolean scheduleSessionMonitoring(String sessionId) {
        return scheduleSessionMonitoring(sessionId, DEFAULT_MONITORING_INTERVAL);
    }
    
    /**
     * Schedule regular monitoring for a simulation session with a custom interval.
     * 
     * @param sessionId The ID of the session to monitor
     * @param intervalMs The monitoring interval in milliseconds
     * @return true if scheduling was successful
     */
    public boolean scheduleSessionMonitoring(String sessionId, long intervalMs) {
        if (sessionId == null || intervalMs <= 0) {
            return false;
        }
        
        // Create task map for this session if it doesn't exist
        scheduledTasks.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        
        // Cancel existing monitoring task if it exists
        cancelTask(sessionId, TaskType.MONITORING);
        
        // Schedule new monitoring task
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
            () -> monitorSession(sessionId),
            intervalMs,
            intervalMs,
            TimeUnit.MILLISECONDS
        );
        
        scheduledTasks.get(sessionId).put(TaskType.MONITORING, future);
        logger.info("Scheduled monitoring for session " + sessionId + " every " + intervalMs + "ms");
        return true;
    }
    
    /**
     * Schedule regular result collection for a simulation session.
     * 
     * @param sessionId The ID of the session
     * @return true if scheduling was successful
     */
    public boolean scheduleResultCollection(String sessionId) {
        return scheduleResultCollection(sessionId, DEFAULT_RESULT_INTERVAL);
    }
    
    /**
     * Schedule regular result collection for a simulation session with a custom interval.
     * 
     * @param sessionId The ID of the session
     * @param intervalMs The collection interval in milliseconds
     * @return true if scheduling was successful
     */
    public boolean scheduleResultCollection(String sessionId, long intervalMs) {
        if (sessionId == null || intervalMs <= 0) {
            return false;
        }
        
        // Create task map for this session if it doesn't exist
        scheduledTasks.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>());
        
        // Cancel existing result collection task if it exists
        cancelTask(sessionId, TaskType.RESULT_COLLECTION);
        
        // Schedule new result collection task
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
            () -> collectResults(sessionId),
            intervalMs,
            intervalMs,
            TimeUnit.MILLISECONDS
        );
        
        scheduledTasks.get(sessionId).put(TaskType.RESULT_COLLECTION, future);
        logger.info("Scheduled result collection for session " + sessionId + " every " + intervalMs + "ms");
        return true;
    }
    
    /**
     * Cancel a specific task for a session.
     * 
     * @param sessionId The ID of the session
     * @param taskType The type of task to cancel
     */
    public void cancelTask(String sessionId, TaskType taskType) {
        Map<TaskType, ScheduledFuture<?>> tasks = scheduledTasks.get(sessionId);
        if (tasks != null) {
            ScheduledFuture<?> future = tasks.get(taskType);
            if (future != null) {
                future.cancel(false);
                tasks.remove(taskType);
                logger.info("Cancelled " + taskType + " task for session " + sessionId);
            }
        }
    }
    
    /**
     * Cancel all tasks for a session.
     * 
     * @param sessionId The ID of the session
     */
    public void cancelSessionTasks(String sessionId) {
        Map<TaskType, ScheduledFuture<?>> tasks = scheduledTasks.get(sessionId);
        if (tasks != null) {
            for (ScheduledFuture<?> future : tasks.values()) {
                future.cancel(false);
            }
            tasks.clear();
            scheduledTasks.remove(sessionId);
            logger.info("Cancelled all tasks for session " + sessionId);
        }
    }
    
    /**
     * Shut down the scheduler and cancel all tasks.
     */
    public void shutdown() {
        // Cancel all tasks
        for (String sessionId : scheduledTasks.keySet()) {
            cancelSessionTasks(sessionId);
        }
        
        // Shut down the executor
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        logger.info("TaskScheduler shut down");
    }
    
    /**
     * Monitor a simulation session.
     * 
     * @param sessionId The ID of the session to monitor
     */
    private void monitorSession(String sessionId) {
        try {
            Map<String, Object> status = nodeController.getSessionStatus(sessionId);
            if (status == null) {
                logger.warning("Failed to get status for session " + sessionId + ", cancelling monitoring");
                cancelTask(sessionId, TaskType.MONITORING);
                return;
            }
            
            // Check if any nodes are disconnected
            Map<String, Map<String, Object>> nodeStatus = 
                (Map<String, Map<String, Object>>) status.get("nodeStatus");
            
            if (nodeStatus != null) {
                for (Map.Entry<String, Map<String, Object>> entry : nodeStatus.entrySet()) {
                    String nodeId = entry.getKey();
                    Map<String, Object> ns = entry.getValue();
                    
                    if (ns.containsKey("error")) {
                        logger.warning("Node " + nodeId + " has error: " + ns.get("error"));
                        // Could implement recovery logic here
                    }
                }
            }
            
            logger.fine("Monitored session " + sessionId + ": " + status);
        } catch (Exception e) {
            logger.severe("Error monitoring session " + sessionId + ": " + e.getMessage());
        }
    }
    
    /**
     * Collect results from a simulation session.
     * 
     * @param sessionId The ID of the session
     */
    private void collectResults(String sessionId) {
        try {
            boolean collected = nodeController.collectResults(sessionId);
            if (!collected) {
                logger.warning("Failed to collect results for session " + sessionId);
            }
        } catch (Exception e) {
            logger.severe("Error collecting results for session " + sessionId + ": " + e.getMessage());
        }
    }
}