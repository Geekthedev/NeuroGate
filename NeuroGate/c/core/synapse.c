#include "synapse.h"
#include "../memory/mm.h"
#include "../utils/log.h"
#include <stdlib.h>
#include <math.h>

// Create a new synapse
Synapse *synapse_create(uint32_t id, uint32_t pre_id, uint32_t post_id, SynapseType type) {
    Synapse *synapse = (Synapse *)mm_alloc(sizeof(Synapse));
    if (!synapse) {
        log_error("Failed to allocate memory for synapse");
        return NULL;
    }

    synapse->id = id;
    synapse->pre_neuron_id = pre_id;
    synapse->post_neuron_id = post_id;
    synapse->type = type;
    synapse->plasticity = STATIC;  // Default to static synapse
    
    // Set default weight based on synapse type
    if (type == EXCITATORY_SYNAPSE) {
        synapse->weight = 0.5f;
    } else if (type == INHIBITORY_SYNAPSE) {
        synapse->weight = -0.5f;
    } else {
        synapse->weight = 0.1f;  // Modulatory synapse
    }
    
    synapse->delay = 1.0f;          // Default delay in ms
    synapse->last_active = -1000.0f; // Set to large negative to ensure proper first activation
    synapse->max_weight = 1.0f;
    synapse->min_weight = -1.0f;
    synapse->user_data = NULL;

    log_debug("Created synapse with ID %u from neuron %u to %u", id, pre_id, post_id);
    return synapse;
}

// Destroy a synapse
void synapse_destroy(Synapse *synapse) {
    if (!synapse) return;
    
    mm_free(synapse);
    log_debug("Destroyed synapse");
}

// Activate a synapse with input from presynaptic neuron
float synapse_activate(Synapse *synapse, float input, float current_time) {
    if (!synapse) {
        log_error("NULL synapse in activate");
        return 0.0f;
    }

    // Check if enough time has passed to account for delay
    if (current_time < synapse->last_active + synapse->delay) {
        return 0.0f;  // Signal hasn't reached postsynaptic neuron yet
    }

    // Record activation time
    synapse->last_active = current_time;
    
    // Apply weight to input
    float output = input * synapse->weight;
    
    log_debug("Synapse %u activated at time %.2f with output %.4f", 
              synapse->id, current_time, output);
    
    return output;
}

// Update synapse weight based on spike timing (STDP)
void synapse_update_weight(Synapse *synapse, float pre_spike_time, float post_spike_time) {
    if (!synapse || synapse->plasticity != STDP) {
        return;  // Only update if STDP is enabled
    }

    float time_diff = post_spike_time - pre_spike_time;
    float weight_change = 0.0f;
    
    // STDP learning rule
    // If post fires after pre (positive time_diff) -> strengthen synapse
    // If post fires before pre (negative time_diff) -> weaken synapse
    float learning_rate = 0.01f;
    float time_constant = 20.0f;  // ms
    
    if (time_diff > 0) {
        // Long-term potentiation (LTP)
        weight_change = learning_rate * expf(-time_diff / time_constant);
    } else {
        // Long-term depression (LTD)
        weight_change = -learning_rate * expf(time_diff / time_constant);
    }
    
    synapse->weight += weight_change;
    
    // Enforce weight bounds
    if (synapse->weight > synapse->max_weight) {
        synapse->weight = synapse->max_weight;
    } else if (synapse->weight < synapse->min_weight) {
        synapse->weight = synapse->min_weight;
    }
    
    log_debug("Updated synapse %u weight to %.4f", synapse->id, synapse->weight);
}

// Reset synapse to initial state
void synapse_reset(Synapse *synapse) {
    if (!synapse) return;
    
    // Reset activation time
    synapse->last_active = -1000.0f;
    
    log_debug("Reset synapse %u", synapse->id);
}