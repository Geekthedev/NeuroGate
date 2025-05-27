#include "exec.h"
#include "../core/neuron.h"
#include "../core/synapse.h"
#include "../memory/mm.h"
#include "../utils/log.h"
#include <stdlib.h>
#include <string.h>

// Global state
static int g_initialized = 0;
static int g_running = 0;

// Simulation state
static Neuron **g_neurons = NULL;
static int g_neuron_count = 0;
static int g_neuron_capacity = 0;
static Synapse **g_synapses = NULL;
static int g_synapse_count = 0;
static int g_synapse_capacity = 0;
static float g_simulation_time = 0.0f;

// Initialize command executor
int exec_init(void) {
    if (g_initialized) {
        log_warn("Command executor already initialized");
        return 0;
    }
    
    // Initialize memory and logging subsystems
    log_init(NULL, LOG_DEBUG);
    mm_init();
    
    // Allocate neuron and synapse arrays
    g_neuron_capacity = 100;
    g_neurons = (Neuron **)mm_alloc(g_neuron_capacity * sizeof(Neuron *));
    if (!g_neurons) {
        log_error("Failed to allocate neuron array");
        return -1;
    }
    
    g_synapse_capacity = 500;
    g_synapses = (Synapse **)mm_alloc(g_synapse_capacity * sizeof(Synapse *));
    if (!g_synapses) {
        mm_free(g_neurons);
        g_neurons = NULL;
        log_error("Failed to allocate synapse array");
        return -1;
    }
    
    g_neuron_count = 0;
    g_synapse_count = 0;
    g_simulation_time = 0.0f;
    
    g_initialized = 1;
    g_running = 1;
    
    log_info("Command executor initialized");
    return 0;
}

// Clean up command executor
void exec_cleanup(void) {
    if (!g_initialized) return;
    
    // Free neurons and synapses
    for (int i = 0; i < g_neuron_count; i++) {
        if (g_neurons[i]) {
            neuron_destroy(g_neurons[i]);
        }
    }
    
    for (int i = 0; i < g_synapse_count; i++) {
        if (g_synapses[i]) {
            synapse_destroy(g_synapses[i]);
        }
    }
    
    mm_free(g_neurons);
    mm_free(g_synapses);
    
    g_neurons = NULL;
    g_synapses = NULL;
    g_neuron_count = 0;
    g_neuron_capacity = 0;
    g_synapse_count = 0;
    g_synapse_capacity = 0;
    
    mm_cleanup();
    log_cleanup();
    
    g_initialized = 0;
    g_running = 0;
    
    log_info("Command executor cleaned up");
}

// Find neuron by ID
static Neuron *find_neuron(uint32_t id) {
    for (int i = 0; i < g_neuron_count; i++) {
        if (g_neurons[i] && g_neurons[i]->id == id) {
            return g_neurons[i];
        }
    }
    return NULL;
}

// Find synapse by ID
static Synapse *find_synapse(uint32_t id) {
    for (int i = 0; i < g_synapse_count; i++) {
        if (g_synapses[i] && g_synapses[i]->id == id) {
            return g_synapses[i];
        }
    }
    return NULL;
}

// Execute a command
command_result_t exec_command(command_type_t type, const command_params_t *params) {
    command_result_t result = {0};
    
    if (!g_initialized) {
        log_error("Command executor not initialized");
        result.status = -1;
        return result;
    }
    
    if (!g_running) {
        log_error("Command executor not running");
        result.status = -1;
        return result;
    }
    
    switch (type) {
        case CMD_NOOP:
            // No operation
            result.status = 0;
            break;
            
        case CMD_CREATE_NEURON: {
            // Create a new neuron
            if (!params) {
                log_error("NULL params for CREATE_NEURON");
                result.status = -1;
                break;
            }
            
            // Check if neuron with this ID already exists
            if (find_neuron(params->neuron_id)) {
                log_error("Neuron with ID %u already exists", params->neuron_id);
                result.status = -1;
                break;
            }
            
            // Create neuron
            Neuron *neuron = neuron_create(
                params->neuron_id,
                (NeuronType)params->neuron_type,
                (ActivationFunction)params->activation_type
            );
            
            if (!neuron) {
                log_error("Failed to create neuron");
                result.status = -1;
                break;
            }
            
            // Set custom parameters if provided
            if (params->threshold != 0.0f) {
                neuron->threshold = params->threshold;
            }
            
            if (params->rest_potential != 0.0f) {
                neuron->rest_potential = params->rest_potential;
            }
            
            if (params->refractory_period != 0.0f) {
                neuron->refractory_period = params->refractory_period;
            }
            
            // Add to array
            if (g_neuron_count >= g_neuron_capacity) {
                // Expand array
                int new_capacity = g_neuron_capacity * 2;
                Neuron **new_array = (Neuron **)mm_realloc(g_neurons, new_capacity * sizeof(Neuron *));
                if (!new_array) {
                    neuron_destroy(neuron);
                    log_error("Failed to expand neuron array");
                    result.status = -1;
                    break;
                }
                g_neurons = new_array;
                g_neuron_capacity = new_capacity;
            }
            
            g_neurons[g_neuron_count++] = neuron;
            
            log_info("Created neuron with ID %u", params->neuron_id);
            result.status = 0;
            result.id = params->neuron_id;
            break;
        }
            
        case CMD_DELETE_NEURON: {
            // Delete a neuron
            if (!params) {
                log_error("NULL params for DELETE_NEURON");
                result.status = -1;
                break;
            }
            
            // Find the neuron
            Neuron *neuron = find_neuron(params->neuron_id);
            if (!neuron) {
                log_error("Neuron with ID %u not found", params->neuron_id);
                result.status = -1;
                break;
            }
            
            // Remove from array
            for (int i = 0; i < g_neuron_count; i++) {
                if (g_neurons[i] == neuron) {
                    // Shift remaining elements
                    for (int j = i; j < g_neuron_count - 1; j++) {
                        g_neurons[j] = g_neurons[j + 1];
                    }
                    g_neuron_count--;
                    break;
                }
            }
            
            // Destroy the neuron
            neuron_destroy(neuron);
            
            log_info("Deleted neuron with ID %u", params->neuron_id);
            result.status = 0;
            break;
        }
            
        case CMD_CONNECT_NEURONS: {
            // Connect two neurons
            if (!params) {
                log_error("NULL params for CONNECT_NEURONS");
                result.status = -1;
                break;
            }
            
            // Find the neurons
            Neuron *source = find_neuron(params->neuron_id);
            Neuron *target = find_neuron(params->target_id);
            
            if (!source || !target) {
                log_error("Source or target neuron not found");
                result.status = -1;
                break;
            }
            
            // Connect them
            int connect_result = neuron_connect(source, target);
            if (connect_result != 0) {
                log_error("Failed to connect neurons %u and %u", params->neuron_id, params->target_id);
                result.status = -1;
                break;
            }
            
            log_info("Connected neurons %u and %u", params->neuron_id, params->target_id);
            result.status = 0;
            break;
        }
            
        case CMD_CREATE_SYNAPSE: {
            // Create a synapse
            if (!params) {
                log_error("NULL params for CREATE_SYNAPSE");
                result.status = -1;
                break;
            }
            
            // Check if synapse with this ID already exists
            if (find_synapse(params->synapse_id)) {
                log_error("Synapse with ID %u already exists", params->synapse_id);
                result.status = -1;
                break;
            }
            
            // Find the neurons
            Neuron *pre = find_neuron(params->neuron_id);
            Neuron *post = find_neuron(params->target_id);
            
            if (!pre || !post) {
                log_error("Pre or post neuron not found");
                result.status = -1;
                break;
            }
            
            // Create synapse
            Synapse *synapse = synapse_create(
                params->synapse_id,
                params->neuron_id,
                params->target_id,
                (SynapseType)params->synapse_type
            );
            
            if (!synapse) {
                log_error("Failed to create synapse");
                result.status = -1;
                break;
            }
            
            // Set custom parameters if provided
            if (params->weight != 0.0f) {
                synapse->weight = params->weight;
            }
            
            if (params->delay != 0.0f) {
                synapse->delay = params->delay;
            }
            
            // Add to array
            if (g_synapse_count >= g_synapse_capacity) {
                // Expand array
                int new_capacity = g_synapse_capacity * 2;
                Synapse **new_array = (Synapse **)mm_realloc(g_synapses, new_capacity * sizeof(Synapse *));
                if (!new_array) {
                    synapse_destroy(synapse);
                    log_error("Failed to expand synapse array");
                    result.status = -1;
                    break;
                }
                g_synapses = new_array;
                g_synapse_capacity = new_capacity;
            }
            
            g_synapses[g_synapse_count++] = synapse;
            
            log_info("Created synapse with ID %u from %u to %u", 
                     params->synapse_id, params->neuron_id, params->target_id);
            result.status = 0;
            result.id = params->synapse_id;
            break;
        }
            
        case CMD_RUN_SIMULATION: {
            // Run simulation for a number of steps
            if (!params) {
                log_error("NULL params for RUN_SIMULATION");
                result.status = -1;
                break;
            }
            
            float time_step = params->time_step > 0.0f ? params->time_step : 1.0f;
            uint32_t num_steps = params->num_steps > 0 ? params->num_steps : 1;
            
            log_info("Running simulation for %u steps with time step %.2f", num_steps, time_step);
            
            // Run simulation
            for (uint32_t step = 0; step < num_steps; step++) {
                // Update simulation time
                g_simulation_time += time_step;
                
                // Process neurons
                for (int i = 0; i < g_neuron_count; i++) {
                    if (g_neurons[i]) {
                        // Compute neuron state
                        neuron_compute(g_neurons[i], 0.0f, time_step);
                        
                        // Check for firing
                        int fired = neuron_fire(g_neurons[i], g_simulation_time);
                        
                        // If neuron fired, propagate signal to connected neurons
                        if (fired) {
                            for (uint32_t j = 0; j < g_neurons[i]->num_connections; j++) {
                                uint32_t target_id = g_neurons[i]->connected_neurons[j];
                                
                                // Find target neuron
                                Neuron *target = find_neuron(target_id);
                                if (target) {
                                    // Find synapse between these neurons
                                    for (int s = 0; s < g_synapse_count; s++) {
                                        if (g_synapses[s] && 
                                            g_synapses[s]->pre_neuron_id == g_neurons[i]->id &&
                                            g_synapses[s]->post_neuron_id == target_id) {
                                            
                                            // Activate synapse
                                            float signal = synapse_activate(g_synapses[s], 1.0f, g_simulation_time);
                                            
                                            // Apply signal to target neuron
                                            target->potential += signal;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            log_info("Simulation completed, time: %.2f", g_simulation_time);
            result.status = 0;
            result.value = g_simulation_time;
            break;
        }
            
        case CMD_RESET_SIMULATION: {
            // Reset simulation state
            for (int i = 0; i < g_neuron_count; i++) {
                if (g_neurons[i]) {
                    neuron_reset(g_neurons[i]);
                }
            }
            
            for (int i = 0; i < g_synapse_count; i++) {
                if (g_synapses[i]) {
                    synapse_reset(g_synapses[i]);
                }
            }
            
            g_simulation_time = 0.0f;
            
            log_info("Simulation reset");
            result.status = 0;
            break;
        }
            
        case CMD_GET_NEURON_STATE: {
            // Get neuron state
            if (!params) {
                log_error("NULL params for GET_NEURON_STATE");
                result.status = -1;
                break;
            }
            
            // Find the neuron
            Neuron *neuron = find_neuron(params->neuron_id);
            if (!neuron) {
                log_error("Neuron with ID %u not found", params->neuron_id);
                result.status = -1;
                break;
            }
            
            result.status = 0;
            result.id = neuron->id;
            result.value = neuron->potential;
            break;
        }
            
        case CMD_SET_NEURON_PARAM: {
            // Set neuron parameter
            if (!params) {
                log_error("NULL params for SET_NEURON_PARAM");
                result.status = -1;
                break;
            }
            
            // Find the neuron
            Neuron *neuron = find_neuron(params->neuron_id);
            if (!neuron) {
                log_error("Neuron with ID %u not found", params->neuron_id);
                result.status = -1;
                break;
            }
            
            // Set the parameter based on target_id (used as parameter ID)
            switch (params->target_id) {
                case 1:  // Threshold
                    neuron->threshold = params->value;
                    break;
                case 2:  // Rest potential
                    neuron->rest_potential = params->value;
                    break;
                case 3:  // Refractory period
                    neuron->refractory_period = params->value;
                    break;
                case 4:  // Potential
                    neuron->potential = params->value;
                    break;
                default:
                    log_error("Unknown parameter ID %u", params->target_id);
                    result.status = -1;
                    return result;
            }
            
            log_info("Set parameter %u of neuron %u to %.4f", 
                     params->target_id, params->neuron_id, params->value);
            result.status = 0;
            break;
        }
            
        case CMD_GET_MEMORY_STATS: {
            // Get memory usage statistics
            result.status = 0;
            result.value = (float)mm_get_used_memory();
            log_info("Memory usage: %zu bytes", (size_t)result.value);
            break;
        }
            
        case CMD_SHUTDOWN: {
            // Shut down the executor
            log_info("Shutdown command received");
            g_running = 0;
            result.status = 0;
            break;
        }
            
        default:
            log_error("Unknown command type %d", type);
            result.status = -1;
            break;
    }
    
    return result;
}

// Process commands from a buffer
int exec_process_buffer(const void *buffer, size_t size, void *result_buffer, size_t *result_size) {
    if (!buffer || !result_buffer || !result_size) {
        log_error("Invalid parameters for process_buffer");
        return -1;
    }
    
    if (!g_initialized || !g_running) {
        log_error("Command executor not initialized or not running");
        return -1;
    }
    
    // This is a placeholder for a real command processing implementation
    // In a real implementation, this would:
    // 1. Parse the buffer to extract commands
    // 2. Execute each command
    // 3. Write results to the result buffer
    
    log_debug("Processing command buffer of size %zu", size);
    
    // Simple example: treat first byte as command type, next few bytes as parameters
    if (size < 1) {
        log_error("Command buffer too small");
        return -1;
    }
    
    const uint8_t *cmd_buffer = (const uint8_t *)buffer;
    command_type_t cmd_type = (command_type_t)cmd_buffer[0];
    
    // Build parameters from buffer
    command_params_t params = {0};
    if (size >= 5) {
        params.neuron_id = cmd_buffer[1];
        params.neuron_type = cmd_buffer[2];
        params.activation_type = cmd_buffer[3];
        params.target_id = cmd_buffer[4];
    }
    
    // Execute command
    command_result_t result = exec_command(cmd_type, &params);
    
    // Write result to buffer
    if (*result_size >= sizeof(command_result_t)) {
        memcpy(result_buffer, &result, sizeof(command_result_t));
        *result_size = sizeof(command_result_t);
    } else {
        log_error("Result buffer too small");
        return -1;
    }
    
    return 0;
}

// Check if executor is running
int exec_is_running(void) {
    return g_running;
}