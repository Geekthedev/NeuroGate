package interop;

/**
 * NeuroBridge provides the Java interface to the C-based NeuroCore library.
 * It uses JNI (Java Native Interface) to call native C functions.
 */
public class NeuroBridge {
    
    // Static initializer to load the native library
    static {
        try {
            System.loadLibrary("neurocore");
            System.out.println("NeuroCore library loaded successfully.");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load NeuroCore library: " + e.getMessage());
            System.err.println("Make sure the library is in the java.library.path");
            throw e;
        }
    }
    
    // Native methods defined in C (bridge.c)
    
    /**
     * Initialize the NeuroCore system.
     * 
     * @return 1 on success, negative value on error
     */
    public native int initCore();
    
    /**
     * Clean up the NeuroCore system.
     */
    public native void cleanupCore();
    
    /**
     * Create a new neuron.
     * 
     * @param id The unique identifier for the neuron
     * @param type The type of neuron (0 = excitatory, 1 = inhibitory)
     * @param activation The activation function (0 = linear, 1 = sigmoid, 2 = relu, 3 = tanh)
     * @return A pointer to the neuron (as a long) or 0 on failure
     */
    public native long createNeuron(int id, int type, int activation);
    
    /**
     * Delete a neuron.
     * 
     * @param neuronPtr Pointer to the neuron (returned from createNeuron)
     */
    public native void deleteNeuron(long neuronPtr);
    
    /**
     * Connect two neurons.
     * 
     * @param sourcePtr Pointer to the source neuron
     * @param targetPtr Pointer to the target neuron
     * @return 0 on success, negative value on error
     */
    public native int connectNeurons(long sourcePtr, long targetPtr);
    
    /**
     * Create a synapse between two neurons.
     * 
     * @param id The unique identifier for the synapse
     * @param preId The ID of the presynaptic neuron
     * @param postId The ID of the postsynaptic neuron
     * @param type The type of synapse (0 = excitatory, 1 = inhibitory, 2 = modulatory)
     * @return A pointer to the synapse (as a long) or 0 on failure
     */
    public native long createSynapse(int id, int preId, int postId, int type);
    
    /**
     * Run a simulation step with the given inputs.
     * 
     * @param inputs Array of input values for input neurons
     * @param timeStep The time step to advance the simulation
     * @return Array of output values from all neurons
     */
    public native float[] runSimulationStep(float[] inputs, float timeStep);
    
    /**
     * Get memory usage statistics.
     * 
     * @return The number of bytes currently allocated
     */
    public native long getMemoryUsage();
    
    // Java wrapper methods for convenience
    
    /**
     * Initialize the system and check for errors.
     * 
     * @throws RuntimeException if initialization fails
     */
    public void initialize() throws RuntimeException {
        int result = initCore();
        if (result <= 0) {
            throw new RuntimeException("Failed to initialize NeuroCore: error code " + result);
        }
    }
    
    /**
     * Clean up and release resources.
     */
    public void shutdown() {
        cleanupCore();
    }
    
    /**
     * Create a neuron with the given parameters.
     * 
     * @param id The unique identifier for the neuron
     * @param type The type of neuron (0 = excitatory, 1 = inhibitory)
     * @param activation The activation function (0 = linear, 1 = sigmoid, 2 = relu, 3 = tanh)
     * @return A neuron handle for later reference
     * @throws RuntimeException if neuron creation fails
     */
    public NeuronHandle createNeuron(int id, NeuronType type, ActivationFunction activation) 
            throws RuntimeException {
        long ptr = createNeuron(id, type.ordinal(), activation.ordinal());
        if (ptr == 0) {
            throw new RuntimeException("Failed to create neuron with ID " + id);
        }
        return new NeuronHandle(id, ptr);
    }
    
    /**
     * Connect two neurons.
     * 
     * @param source The source neuron handle
     * @param target The target neuron handle
     * @throws RuntimeException if connection fails
     */
    public void connectNeurons(NeuronHandle source, NeuronHandle target) throws RuntimeException {
        int result = connectNeurons(source.getPointer(), target.getPointer());
        if (result != 0) {
            throw new RuntimeException(
                "Failed to connect neurons " + source.getId() + " and " + target.getId()
            );
        }
    }
    
    /**
     * Create a synapse between two neurons.
     * 
     * @param id The unique identifier for the synapse
     * @param source The source neuron handle
     * @param target The target neuron handle
     * @param type The type of synapse
     * @return A synapse handle for later reference
     * @throws RuntimeException if synapse creation fails
     */
    public SynapseHandle createSynapse(int id, NeuronHandle source, NeuronHandle target, SynapseType type) 
            throws RuntimeException {
        long ptr = createSynapse(id, source.getId(), target.getId(), type.ordinal());
        if (ptr == 0) {
            throw new RuntimeException("Failed to create synapse with ID " + id);
        }
        return new SynapseHandle(id, ptr);
    }
    
    /**
     * Run a simulation step.
     * 
     * @param inputs Input values for the simulation
     * @param timeStep Time step size
     * @return Output values from all neurons
     */
    public float[] runSimulation(float[] inputs, float timeStep) {
        return runSimulationStep(inputs, timeStep);
    }
    
    /**
     * Get current memory usage.
     * 
     * @return Memory usage in bytes
     */
    public long getMemoryUsage() {
        return getMemoryUsage();
    }
    
    // Neuron types enum
    public enum NeuronType {
        EXCITATORY,
        INHIBITORY
    }
    
    // Activation function enum
    public enum ActivationFunction {
        LINEAR,
        SIGMOID,
        RELU,
        TANH
    }
    
    // Synapse types enum
    public enum SynapseType {
        EXCITATORY,
        INHIBITORY,
        MODULATORY
    }
    
    // Handle classes for neurons and synapses
    
    /**
     * Handle for a neuron in the NeuroCore system.
     */
    public static class NeuronHandle {
        private final int id;
        private final long pointer;
        
        public NeuronHandle(int id, long pointer) {
            this.id = id;
            this.pointer = pointer;
        }
        
        public int getId() {
            return id;
        }
        
        public long getPointer() {
            return pointer;
        }
    }
    
    /**
     * Handle for a synapse in the NeuroCore system.
     */
    public static class SynapseHandle {
        private final int id;
        private final long pointer;
        
        public SynapseHandle(int id, long pointer) {
            this.id = id;
            this.pointer = pointer;
        }
        
        public int getId() {
            return id;
        }
        
        public long getPointer() {
            return pointer;
        }
    }
}