#include "neuron.h"
#include "../memory/mm.h"
#include "../utils/log.h"
#include <stdlib.h>
#include <math.h>
#include <string.h>

// Create a new neuron
Neuron *neuron_create(uint32_t id, NeuronType type, ActivationFunction activation) {
    Neuron *neuron = (Neuron *)mm_alloc(sizeof(Neuron));
    if (!neuron) {
        log_error("Failed to allocate memory for neuron");
        return NULL;
    }

    neuron->id = id;
    neuron->type = type;
    neuron->activation = activation;
    neuron->potential = -70.0f;         // Default resting potential in mV
    neuron->threshold = -55.0f;         // Default threshold in mV
    neuron->rest_potential = -70.0f;    // Default resting potential in mV
    neuron->refractory_period = 2.0f;   // Default refractory period in ms
    neuron->last_fired = -1000.0f;      // Set to a large negative to ensure it can fire immediately
    neuron->connected_neurons = NULL;
    neuron->num_connections = 0;
    neuron->user_data = NULL;

    log_debug("Created neuron with ID %u", id);
    return neuron;
}

// Destroy a neuron
void neuron_destroy(Neuron *neuron) {
    if (!neuron) return;
    
    if (neuron->connected_neurons) {
        mm_free(neuron->connected_neurons);
    }
    
    mm_free(neuron);
    log_debug("Destroyed neuron");
}

// Connect two neurons
int neuron_connect(Neuron *source, Neuron *target) {
    if (!source || !target) {
        log_error("Invalid neuron pointers for connection");
        return -1;
    }

    // Check if connection already exists
    for (uint32_t i = 0; i < source->num_connections; i++) {
        if (source->connected_neurons[i] == target->id) {
            log_warn("Connection already exists between neurons %u and %u", source->id, target->id);
            return 0;
        }
    }

    // Allocate or reallocate memory for connections
    uint32_t *new_connections = (uint32_t *)mm_realloc(
        source->connected_neurons, 
        (source->num_connections + 1) * sizeof(uint32_t)
    );
    
    if (!new_connections) {
        log_error("Failed to allocate memory for neuron connection");
        return -1;
    }

    source->connected_neurons = new_connections;
    source->connected_neurons[source->num_connections] = target->id;
    source->num_connections++;

    log_debug("Connected neuron %u to %u", source->id, target->id);
    return 0;
}

// Disconnect two neurons
int neuron_disconnect(Neuron *source, Neuron *target) {
    if (!source || !target) {
        log_error("Invalid neuron pointers for disconnection");
        return -1;
    }

    // Find the connection
    int found = -1;
    for (uint32_t i = 0; i < source->num_connections; i++) {
        if (source->connected_neurons[i] == target->id) {
            found = i;
            break;
        }
    }

    if (found == -1) {
        log_warn("No connection exists between neurons %u and %u", source->id, target->id);
        return 0;
    }

    // Remove the connection by shifting elements
    for (uint32_t i = found; i < source->num_connections - 1; i++) {
        source->connected_neurons[i] = source->connected_neurons[i + 1];
    }

    source->num_connections--;

    // Reallocate memory if needed
    if (source->num_connections > 0) {
        uint32_t *new_connections = (uint32_t *)mm_realloc(
            source->connected_neurons, 
            source->num_connections * sizeof(uint32_t)
        );
        
        if (new_connections) {
            source->connected_neurons = new_connections;
        }
    } else {
        mm_free(source->connected_neurons);
        source->connected_neurons = NULL;
    }

    log_debug("Disconnected neuron %u from %u", source->id, target->id);
    return 0;
}

// Apply activation function
static float apply_activation(ActivationFunction func, float value) {
    switch (func) {
        case LINEAR:
            return value;
        case SIGMOID:
            return 1.0f / (1.0f + expf(-value));
        case RELU:
            return value > 0 ? value : 0;
        case TANH:
            return tanhf(value);
        default:
            return value;
    }
}

// Compute neuron state for a time step
float neuron_compute(Neuron *neuron, float input, float dt) {
    if (!neuron) {
        log_error("NULL neuron in compute");
        return 0.0f;
    }

    // Update potential based on input and time step
    neuron->potential += input * dt;
    
    // Apply leak (simple exponential decay toward rest potential)
    float leak_rate = 0.1f;
    neuron->potential = neuron->potential * (1.0f - leak_rate) + 
                        neuron->rest_potential * leak_rate;
    
    return apply_activation(neuron->activation, neuron->potential);
}

// Determine if a neuron should fire based on its potential and refractory period
int neuron_fire(Neuron *neuron, float current_time) {
    if (!neuron) {
        log_error("NULL neuron in fire check");
        return 0;
    }

    // Check if neuron is in refractory period
    if (current_time - neuron->last_fired < neuron->refractory_period) {
        return 0;
    }

    // Check if potential is above threshold
    if (neuron->potential >= neuron->threshold) {
        neuron->last_fired = current_time;
        log_debug("Neuron %u fired at time %.2f", neuron->id, current_time);
        
        // Reset potential after firing
        neuron->potential = neuron->rest_potential;
        return 1;
    }

    return 0;
}

// Reset neuron to initial state
void neuron_reset(Neuron *neuron) {
    if (!neuron) return;
    
    neuron->potential = neuron->rest_potential;
    neuron->last_fired = -1000.0f;
    
    log_debug("Reset neuron %u", neuron->id);
}