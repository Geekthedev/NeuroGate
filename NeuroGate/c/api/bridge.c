#include "bridge.h"
#include "../core/neuron.h"
#include "../core/synapse.h"
#include "../memory/mm.h"
#include "../utils/log.h"
#include <stdlib.h>
#include <string.h>

// Global simulation state
static int g_initialized = 0;
static Neuron **g_neurons = NULL;
static int g_neuron_count = 0;
static int g_neuron_capacity = 0;
static Synapse **g_synapses = NULL;
static int g_synapse_count = 0;
static int g_synapse_capacity = 0;
static float g_simulation_time = 0.0f;

// Initialize the NeuroCore system
JNIEXPORT jint JNICALL Java_interop_NeuroBridge_initCore(JNIEnv *env, jobject obj) {
    if (g_initialized) {
        log_warn("NeuroCore already initialized");
        return 0;
    }
    
    // Initialize subsystems
    log_init(NULL, LOG_DEBUG);
    mm_init();
    
    // Allocate initial neuron and synapse arrays
    g_neuron_capacity = 100;  // Initial capacity
    g_neurons = (Neuron **)mm_alloc(g_neuron_capacity * sizeof(Neuron *));
    if (!g_neurons) {
        log_error("Failed to allocate neuron array");
        return -1;
    }
    
    g_synapse_capacity = 500;  // Initial capacity
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
    
    log_info("NeuroCore initialized");
    return 1;
}

// Clean up the NeuroCore system
JNIEXPORT void JNICALL Java_interop_NeuroBridge_cleanupCore(JNIEnv *env, jobject obj) {
    if (!g_initialized) {
        return;
    }
    
    // Free all neurons
    for (int i = 0; i < g_neuron_count; i++) {
        if (g_neurons[i]) {
            neuron_destroy(g_neurons[i]);
        }
    }
    
    // Free all synapses
    for (int i = 0; i < g_synapse_count; i++) {
        if (g_synapses[i]) {
            synapse_destroy(g_synapses[i]);
        }
    }
    
    // Free arrays
    mm_free(g_neurons);
    mm_free(g_synapses);
    
    g_neurons = NULL;
    g_synapses = NULL;
    g_neuron_count = 0;
    g_synapse_count = 0;
    
    // Clean up subsystems
    mm_cleanup();
    log_cleanup();
    
    g_initialized = 0;
    log_info("NeuroCore cleaned up");
}

// Create a new neuron
JNIEXPORT jlong JNICALL Java_interop_NeuroBridge_createNeuron(
    JNIEnv *env, jobject obj, jint id, jint type, jint activation) {
    
    if (!g_initialized) {
        log_error("NeuroCore not initialized");
        return 0;
    }
    
    // Check if we need to expand the neuron array
    if (g_neuron_count >= g_neuron_capacity) {
        int new_capacity = g_neuron_capacity * 2;
        Neuron **new_neurons = (Neuron **)mm_realloc(g_neurons, new_capacity * sizeof(Neuron *));
        if (!new_neurons) {
            log_error("Failed to expand neuron array");
            return 0;
        }
        g_neurons = new_neurons;
        g_neuron_capacity = new_capacity;
    }
    
    // Create the neuron
    Neuron *neuron = neuron_create(id, (NeuronType)type, (ActivationFunction)activation);
    if (!neuron) {
        return 0;
    }
    
    // Add to array
    g_neurons[g_neuron_count++] = neuron;
    
    log_debug("Created neuron with ID %d (JNI)", id);
    return (jlong)neuron;
}

// Delete a neuron
JNIEXPORT void JNICALL Java_interop_NeuroBridge_deleteNeuron(
    JNIEnv *env, jobject obj, jlong neuronPtr) {
    
    if (!g_initialized) {
        return;
    }
    
    Neuron *neuron = (Neuron *)neuronPtr;
    
    // Find and remove from array
    for (int i = 0; i < g_neuron_count; i++) {
        if (g_neurons[i] == neuron) {
            // Shift remaining neurons
            for (int j = i; j < g_neuron_count - 1; j++) {
                g_neurons[j] = g_neurons[j + 1];
            }
            g_neuron_count--;
            break;
        }
    }
    
    // Destroy the neuron
    neuron_destroy(neuron);
    log_debug("Deleted neuron (JNI)");
}

// Connect two neurons
JNIEXPORT jint JNICALL Java_interop_NeuroBridge_connectNeurons(
    JNIEnv *env, jobject obj, jlong sourcePtr, jlong targetPtr) {
    
    if (!g_initialized) {
        log_error("NeuroCore not initialized");
        return -1;
    }
    
    Neuron *source = (Neuron *)sourcePtr;
    Neuron *target = (Neuron *)targetPtr;
    
    return neuron_connect(source, target);
}

// Create a synapse between two neurons
JNIEXPORT jlong JNICALL Java_interop_NeuroBridge_createSynapse(
    JNIEnv *env, jobject obj, jint id, jint preId, jint postId, jint type) {
    
    if (!g_initialized) {
        log_error("NeuroCore not initialized");
        return 0;
    }
    
    // Check if we need to expand the synapse array
    if (g_synapse_count >= g_synapse_capacity) {
        int new_capacity = g_synapse_capacity * 2;
        Synapse **new_synapses = (Synapse **)mm_realloc(g_synapses, new_capacity * sizeof(Synapse *));
        if (!new_synapses) {
            log_error("Failed to expand synapse array");
            return 0;
        }
        g_synapses = new_synapses;
        g_synapse_capacity = new_capacity;
    }
    
    // Create the synapse
    Synapse *synapse = synapse_create(id, preId, postId, (SynapseType)type);
    if (!synapse) {
        return 0;
    }
    
    // Add to array
    g_synapses[g_synapse_count++] = synapse;
    
    log_debug("Created synapse with ID %d from %d to %d (JNI)", id, preId, postId);
    return (jlong)synapse;
}

// Run a simulation step
JNIEXPORT jfloatArray JNICALL Java_interop_NeuroBridge_runSimulationStep(
    JNIEnv *env, jobject obj, jfloatArray inputs, jfloat timeStep) {
    
    if (!g_initialized) {
        log_error("NeuroCore not initialized");
        return NULL;
    }
    
    // Get input array
    jsize input_length = (*env)->GetArrayLength(env, inputs);
    jfloat *input_data = (*env)->GetFloatArrayElements(env, inputs, NULL);
    
    if (input_length > g_neuron_count) {
        input_length = g_neuron_count;  // Limit to available neurons
    }
    
    // Apply inputs to input neurons
    for (int i = 0; i < input_length; i++) {
        if (g_neurons[i]) {
            g_neurons[i]->potential += input_data[i];
        }
    }
    
    // Release input array
    (*env)->ReleaseFloatArrayElements(env, inputs, input_data, JNI_ABORT);
    
    // Update simulation time
    g_simulation_time += timeStep;
    
    // Process all neurons
    float *outputs = (float *)mm_alloc(g_neuron_count * sizeof(float));
    if (!outputs) {
        log_error("Failed to allocate output array");
        return NULL;
    }
    
    for (int i = 0; i < g_neuron_count; i++) {
        if (g_neurons[i]) {
            // Compute neuron state
            outputs[i] = neuron_compute(g_neurons[i], 0.0f, timeStep);
            
            // Check for firing
            int fired = neuron_fire(g_neurons[i], g_simulation_time);
            
            // If neuron fired, propagate signal to connected neurons
            if (fired) {
                for (uint32_t j = 0; j < g_neurons[i]->num_connections; j++) {
                    uint32_t target_id = g_neurons[i]->connected_neurons[j];
                    
                    // Find target neuron
                    for (int k = 0; k < g_neuron_count; k++) {
                        if (g_neurons[k] && g_neurons[k]->id == target_id) {
                            // Find synapse between these neurons
                            for (int s = 0; s < g_synapse_count; s++) {
                                if (g_synapses[s] && 
                                    g_synapses[s]->pre_neuron_id == g_neurons[i]->id &&
                                    g_synapses[s]->post_neuron_id == target_id) {
                                    
                                    // Activate synapse
                                    float signal = synapse_activate(g_synapses[s], 1.0f, g_simulation_time);
                                    
                                    // Apply signal to target neuron
                                    g_neurons[k]->potential += signal;
                                    break;
                                }
                            }
                            break;
                        }
                    }
                }
            }
        } else {
            outputs[i] = 0.0f;
        }
    }
    
    // Create output array
    jfloatArray result = (*env)->NewFloatArray(env, g_neuron_count);
    if (result == NULL) {
        mm_free(outputs);
        return NULL;
    }
    
    (*env)->SetFloatArrayRegion(env, result, 0, g_neuron_count, outputs);
    
    // Free temporary array
    mm_free(outputs);
    
    return result;
}

// Get memory usage statistics
JNIEXPORT jlong JNICALL Java_interop_NeuroBridge_getMemoryUsage(
    JNIEnv *env, jobject obj) {
    
    if (!g_initialized) {
        return 0;
    }
    
    return (jlong)mm_get_used_memory();
}