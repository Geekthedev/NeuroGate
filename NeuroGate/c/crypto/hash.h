#ifndef HASH_H
#define HASH_H

#include <stdint.h>
#include <stddef.h>

// Hash algorithm types
typedef enum {
    HASH_SHA256,
    HASH_BLAKE2
} hash_algorithm_t;

// Context structure for SHA-256
typedef struct {
    uint32_t state[8];      // Hash state
    uint64_t bit_count;     // Bits processed
    uint8_t buffer[64];     // Input buffer
    uint32_t buffer_index;  // Current position in buffer
} sha256_context_t;

// Context structure for BLAKE2
typedef struct {
    uint64_t h[8];          // Hash state
    uint64_t t[2];          // Counter
    uint64_t f[2];          // Finalization flags
    uint8_t buffer[128];    // Input buffer
    uint32_t buffer_index;  // Current position in buffer
    uint32_t outlen;        // Output length
} blake2_context_t;

// Hash context union
typedef union {
    sha256_context_t sha256;
    blake2_context_t blake2;
} hash_context_t;

// Hash state structure
typedef struct {
    hash_algorithm_t algorithm;
    hash_context_t context;
} hash_state_t;

// Initialize hash state
int hash_init(hash_state_t *state, hash_algorithm_t algorithm);

// Update hash with data
int hash_update(hash_state_t *state, const void *data, size_t length);

// Finalize hash and get digest
int hash_final(hash_state_t *state, uint8_t *digest);

// One-shot hash function
int hash_data(hash_algorithm_t algorithm, const void *data, size_t length, uint8_t *digest);

// Get digest length for algorithm
size_t hash_get_digest_length(hash_algorithm_t algorithm);

// Reset hash state for reuse
void hash_reset(hash_state_t *state);

// Helper functions
void hash_to_hex(const uint8_t *digest, size_t length, char *hex_str);
int hash_compare(const uint8_t *a, const uint8_t *b, size_t length);

#endif // HASH_H