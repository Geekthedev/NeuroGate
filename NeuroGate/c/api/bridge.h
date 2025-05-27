#ifndef BRIDGE_H
#define BRIDGE_H

#include <jni.h>
#include "../core/neuron.h"
#include "../core/synapse.h"

// JNI function declarations
#ifdef __cplusplus
extern "C" {
#endif

// Initialize the NeuroCore system
JNIEXPORT jint JNICALL Java_interop_NeuroBridge_initCore(JNIEnv *env, jobject obj);

// Clean up the NeuroCore system
JNIEXPORT void JNICALL Java_interop_NeuroBridge_cleanupCore(JNIEnv *env, jobject obj);

// Create a new neuron
JNIEXPORT jlong JNICALL Java_interop_NeuroBridge_createNeuron(
    JNIEnv *env, jobject obj, jint id, jint type, jint activation);

// Delete a neuron
JNIEXPORT void JNICALL Java_interop_NeuroBridge_deleteNeuron(
    JNIEnv *env, jobject obj, jlong neuronPtr);

// Connect two neurons
JNIEXPORT jint JNICALL Java_interop_NeuroBridge_connectNeurons(
    JNIEnv *env, jobject obj, jlong sourcePtr, jlong targetPtr);

// Create a synapse between two neurons
JNIEXPORT jlong JNICALL Java_interop_NeuroBridge_createSynapse(
    JNIEnv *env, jobject obj, jint id, jint preId, jint postId, jint type);

// Run a simulation step
JNIEXPORT jfloatArray JNICALL Java_interop_NeuroBridge_runSimulationStep(
    JNIEnv *env, jobject obj, jfloatArray inputs, jfloat timeStep);

// Get memory usage statistics
JNIEXPORT jlong JNICALL Java_interop_NeuroBridge_getMemoryUsage(
    JNIEnv *env, jobject obj);

#ifdef __cplusplus
}
#endif

#endif // BRIDGE_H