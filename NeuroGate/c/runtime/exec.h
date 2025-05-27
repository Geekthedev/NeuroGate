#ifndef EXEC_H
#define EXEC_H

#include <stdint.h>
#include <stddef.h>

// Command types
typedef enum {
    CMD_NOOP,
    CMD_CREATE_NEURON,
    CMD_DELETE_NEURON,
    CMD_CONNECT_NEURONS,
    CMD_CREATE_SYNAPSE,
    CMD_RUN_SIMULATION,
    CMD_RESET_SIMULATION,
    CMD_GET_NEURON_STATE,
    CMD_SET_NEURON_PARAM,
    CMD_GET_MEMORY_STATS,
    CMD_SHUTDOWN
} command_type_t;

// Command parameters
typedef struct {
    uint32_t neuron_id;
    uint32_t neuron_type;
    uint32_t activation_type;
    float threshold;
    float rest_potential;
    float refractory_period;
    uint32_t target_id;
    uint32_t synapse_id;
    uint32_t synapse_type;
    float weight;
    float delay;
    float sim_time;
    float time_step;
    uint32_t num_steps;
    const void *data;
    size_t data_size;
} command_params_t;

// Command result
typedef struct {
    int status;
    uint32_t id;
    float value;
    void *data;
    size_t data_size;
} command_result_t;

// Initialize command executor
int exec_init(void);

// Clean up command executor
void exec_cleanup(void);

// Execute a command
command_result_t exec_command(command_type_t type, const command_params_t *params);

// Process commands from a buffer
int exec_process_buffer(const void *buffer, size_t size, void *result_buffer, size_t *result_size);

// Check if executor is running
int exec_is_running(void);

#endif // EXEC_H