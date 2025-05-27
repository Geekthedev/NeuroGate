#ifndef NEURON_H
#define NEURON_H

#include <stdint.h>

// Neuron types
typedef enum {
    EXCITATORY,
    INHIBITORY
} NeuronType;

// Neuron activation functions
typedef enum {
    LINEAR,
    SIGMOID,
    RELU,
    TANH
} ActivationFunction;

// Neuron structure
typedef struct {
    uint32_t id;                 // Unique ID
    NeuronType type;             // Type of neuron
    ActivationFunction activation; // Activation function
    float potential;             // Current membrane potential
    float threshold;             // Firing threshold
    float rest_potential;        // Resting potential
    float refractory_period;     // Refractory period in ms
    float last_fired;            // Time of last firing in ms
    uint32_t *connected_neurons; // Array of connected neuron IDs
    uint32_t num_connections;    // Number of connections
    void *user_data;             // Optional user data
} Neuron;

// Function declarations
Neuron *neuron_create(uint32_t id, NeuronType type, ActivationFunction activation);
void neuron_destroy(Neuron *neuron);
int neuron_connect(Neuron *source, Neuron *target);
int neuron_disconnect(Neuron *source, Neuron *target);
float neuron_compute(Neuron *neuron, float input, float dt);
int neuron_fire(Neuron *neuron, float current_time);
void neuron_reset(Neuron *neuron);

#endif // NEURON_H