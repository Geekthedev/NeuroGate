#ifndef SYNAPSE_H
#define SYNAPSE_H

#include <stdint.h>

// Synapse types
typedef enum {
    EXCITATORY_SYNAPSE,
    INHIBITORY_SYNAPSE,
    MODULATORY_SYNAPSE
} SynapseType;

// Synapse plasticity types
typedef enum {
    STATIC,         // No weight changes
    STDP,           // Spike-timing-dependent plasticity
    HEBBIAN,        // Basic Hebbian learning
    HOMEOSTATIC     // Homeostatic plasticity
} PlasticityType;

// Synapse structure
typedef struct {
    uint32_t id;                // Unique ID
    uint32_t pre_neuron_id;     // ID of presynaptic neuron
    uint32_t post_neuron_id;    // ID of postsynaptic neuron
    SynapseType type;           // Type of synapse
    PlasticityType plasticity;  // Type of plasticity
    float weight;               // Synaptic weight
    float delay;                // Transmission delay in ms
    float last_active;          // Time of last activation
    float max_weight;           // Maximum weight value
    float min_weight;           // Minimum weight value
    void *user_data;            // Optional user data
} Synapse;

// Function declarations
Synapse *synapse_create(uint32_t id, uint32_t pre_id, uint32_t post_id, SynapseType type);
void synapse_destroy(Synapse *synapse);
float synapse_activate(Synapse *synapse, float input, float current_time);
void synapse_update_weight(Synapse *synapse, float pre_spike_time, float post_spike_time);
void synapse_reset(Synapse *synapse);

#endif // SYNAPSE_H