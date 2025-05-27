#include "hash.h"
#include "../utils/log.h"
#include "../memory/mm.h"
#include <string.h>
#include <stdio.h>

// SHA-256 constants
static const uint32_t sha256_k[64] = {
    0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
    0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
    0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
    0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
    0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
    0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
    0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
    0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
};

// BLAKE2 initialization vectors
static const uint64_t blake2_iv[8] = {
    0x6a09e667f3bcc908ULL, 0xbb67ae8584caa73bULL,
    0x3c6ef372fe94f82bULL, 0xa54ff53a5f1d36f1ULL,
    0x510e527fade682d1ULL, 0x9b05688c2b3e6c1fULL,
    0x1f83d9abfb41bd6bULL, 0x5be0cd19137e2179ULL
};

// SHA-256 functions
#define SHA256_CH(x, y, z) (((x) & (y)) ^ (~(x) & (z)))
#define SHA256_MAJ(x, y, z) (((x) & (y)) ^ ((x) & (z)) ^ ((y) & (z)))
#define SHA256_EP0(x) (ROTR32(x, 2) ^ ROTR32(x, 13) ^ ROTR32(x, 22))
#define SHA256_EP1(x) (ROTR32(x, 6) ^ ROTR32(x, 11) ^ ROTR32(x, 25))
#define SHA256_SIG0(x) (ROTR32(x, 7) ^ ROTR32(x, 18) ^ ((x) >> 3))
#define SHA256_SIG1(x) (ROTR32(x, 17) ^ ROTR32(x, 19) ^ ((x) >> 10))
#define ROTR32(x, n) (((x) >> (n)) | ((x) << (32 - (n))))
#define ROTR64(x, n) (((x) >> (n)) | ((x) << (64 - (n))))

// Initialize hash state
int hash_init(hash_state_t *state, hash_algorithm_t algorithm) {
    if (!state) {
        log_error("NULL state pointer in hash_init");
        return -1;
    }
    
    state->algorithm = algorithm;
    
    switch (algorithm) {
        case HASH_SHA256:
            // Initialize SHA-256 state
            state->context.sha256.state[0] = 0x6a09e667;
            state->context.sha256.state[1] = 0xbb67ae85;
            state->context.sha256.state[2] = 0x3c6ef372;
            state->context.sha256.state[3] = 0xa54ff53a;
            state->context.sha256.state[4] = 0x510e527f;
            state->context.sha256.state[5] = 0x9b05688c;
            state->context.sha256.state[6] = 0x1f83d9ab;
            state->context.sha256.state[7] = 0x5be0cd19;
            state->context.sha256.bit_count = 0;
            state->context.sha256.buffer_index = 0;
            break;
            
        case HASH_BLAKE2:
            // Initialize BLAKE2 state (simplified for this example)
            memcpy(state->context.blake2.h, blake2_iv, 8 * sizeof(uint64_t));
            state->context.blake2.t[0] = 0;
            state->context.blake2.t[1] = 0;
            state->context.blake2.f[0] = 0;
            state->context.blake2.f[1] = 0;
            state->context.blake2.buffer_index = 0;
            state->context.blake2.outlen = 32;  // Default to 32 bytes output
            break;
            
        default:
            log_error("Unknown hash algorithm %d", algorithm);
            return -1;
    }
    
    log_debug("Initialized hash context for algorithm %d", algorithm);
    return 0;
}

// Transform function for SHA-256
static void sha256_transform(sha256_context_t *ctx) {
    uint32_t a, b, c, d, e, f, g, h, t1, t2;
    uint32_t m[64];
    uint32_t i, j;
    
    // Prepare message schedule
    for (i = 0, j = 0; i < 16; i++, j += 4) {
        m[i] = ((uint32_t)ctx->buffer[j] << 24) |
               ((uint32_t)ctx->buffer[j + 1] << 16) |
               ((uint32_t)ctx->buffer[j + 2] << 8) |
               ((uint32_t)ctx->buffer[j + 3]);
    }
    
    for (; i < 64; i++) {
        m[i] = SHA256_SIG1(m[i - 2]) + m[i - 7] + SHA256_SIG0(m[i - 15]) + m[i - 16];
    }
    
    // Initialize working variables
    a = ctx->state[0];
    b = ctx->state[1];
    c = ctx->state[2];
    d = ctx->state[3];
    e = ctx->state[4];
    f = ctx->state[5];
    g = ctx->state[6];
    h = ctx->state[7];
    
    // Compression function main loop
    for (i = 0; i < 64; i++) {
        t1 = h + SHA256_EP1(e) + SHA256_CH(e, f, g) + sha256_k[i] + m[i];
        t2 = SHA256_EP0(a) + SHA256_MAJ(a, b, c);
        h = g;
        g = f;
        f = e;
        e = d + t1;
        d = c;
        c = b;
        b = a;
        a = t1 + t2;
    }
    
    // Update state
    ctx->state[0] += a;
    ctx->state[1] += b;
    ctx->state[2] += c;
    ctx->state[3] += d;
    ctx->state[4] += e;
    ctx->state[5] += f;
    ctx->state[6] += g;
    ctx->state[7] += h;
}

// Transform function for BLAKE2 (simplified)
static void blake2_transform(blake2_context_t *ctx) {
    // This is a simplified placeholder for a real BLAKE2 implementation
    // In a real implementation, this would perform the BLAKE2 compression function
    log_debug("BLAKE2 transform (simplified placeholder)");
    
    // Increment counter
    ctx->t[0] += ctx->buffer_index;
    if (ctx->t[0] < ctx->buffer_index) {
        ctx->t[1]++;
    }
}

// Update hash with data
int hash_update(hash_state_t *state, const void *data, size_t length) {
    if (!state || !data) {
        log_error("Invalid parameters for hash_update");
        return -1;
    }
    
    const uint8_t *input = (const uint8_t *)data;
    
    switch (state->algorithm) {
        case HASH_SHA256: {
            sha256_context_t *ctx = &state->context.sha256;
            
            // Update bit count
            ctx->bit_count += length * 8;
            
            // Process input data
            while (length > 0) {
                // Copy data to buffer until full or out of data
                size_t copy_len = 64 - ctx->buffer_index;
                if (copy_len > length) {
                    copy_len = length;
                }
                
                memcpy(ctx->buffer + ctx->buffer_index, input, copy_len);
                ctx->buffer_index += copy_len;
                input += copy_len;
                length -= copy_len;
                
                // Process block if buffer is full
                if (ctx->buffer_index == 64) {
                    sha256_transform(ctx);
                    ctx->buffer_index = 0;
                }
            }
            break;
        }
            
        case HASH_BLAKE2: {
            blake2_context_t *ctx = &state->context.blake2;
            
            // Process input data
            while (length > 0) {
                // Copy data to buffer until full or out of data
                size_t copy_len = 128 - ctx->buffer_index;
                if (copy_len > length) {
                    copy_len = length;
                }
                
                memcpy(ctx->buffer + ctx->buffer_index, input, copy_len);
                ctx->buffer_index += copy_len;
                input += copy_len;
                length -= copy_len;
                
                // Process block if buffer is full
                if (ctx->buffer_index == 128) {
                    blake2_transform(ctx);
                    ctx->buffer_index = 0;
                }
            }
            break;
        }
            
        default:
            log_error("Unknown hash algorithm %d", state->algorithm);
            return -1;
    }
    
    return 0;
}

// Finalize hash and get digest
int hash_final(hash_state_t *state, uint8_t *digest) {
    if (!state || !digest) {
        log_error("Invalid parameters for hash_final");
        return -1;
    }
    
    switch (state->algorithm) {
        case HASH_SHA256: {
            sha256_context_t *ctx = &state->context.sha256;
            uint32_t i;
            
            // Add padding
            i = ctx->buffer_index;
            
            // Pad with 1 bit followed by zeros
            if (i < 56) {
                ctx->buffer[i++] = 0x80;
                // Zero pad the rest
                memset(ctx->buffer + i, 0, 56 - i);
            } else {
                // Not enough room for padding and length
                ctx->buffer[i++] = 0x80;
                // Zero pad the rest
                memset(ctx->buffer + i, 0, 64 - i);
                // Process this block
                sha256_transform(ctx);
                // Zero pad the next block
                memset(ctx->buffer, 0, 56);
            }
            
            // Append bit length
            uint64_t bit_count = ctx->bit_count;
            ctx->buffer[56] = (bit_count >> 56) & 0xff;
            ctx->buffer[57] = (bit_count >> 48) & 0xff;
            ctx->buffer[58] = (bit_count >> 40) & 0xff;
            ctx->buffer[59] = (bit_count >> 32) & 0xff;
            ctx->buffer[60] = (bit_count >> 24) & 0xff;
            ctx->buffer[61] = (bit_count >> 16) & 0xff;
            ctx->buffer[62] = (bit_count >> 8) & 0xff;
            ctx->buffer[63] = bit_count & 0xff;
            
            // Process final block
            sha256_transform(ctx);
            
            // Copy digest to output
            for (i = 0; i < 8; i++) {
                digest[i * 4] = (ctx->state[i] >> 24) & 0xff;
                digest[i * 4 + 1] = (ctx->state[i] >> 16) & 0xff;
                digest[i * 4 + 2] = (ctx->state[i] >> 8) & 0xff;
                digest[i * 4 + 3] = ctx->state[i] & 0xff;
            }
            break;
        }
            
        case HASH_BLAKE2: {
            blake2_context_t *ctx = &state->context.blake2;
            
            // Set last block flag
            ctx->f[0] = 0xffffffffffffffffULL;
            
            // Pad remaining data
            if (ctx->buffer_index < 128) {
                memset(ctx->buffer + ctx->buffer_index, 0, 128 - ctx->buffer_index);
            }
            
            // Process final block
            blake2_transform(ctx);
            
            // Copy digest to output (simplified)
            for (int i = 0; i < 8; i++) {
                uint64_t h = ctx->h[i];
                digest[i * 8] = (h >> 56) & 0xff;
                digest[i * 8 + 1] = (h >> 48) & 0xff;
                digest[i * 8 + 2] = (h >> 40) & 0xff;
                digest[i * 8 + 3] = (h >> 32) & 0xff;
                digest[i * 8 + 4] = (h >> 24) & 0xff;
                digest[i * 8 + 5] = (h >> 16) & 0xff;
                digest[i * 8 + 6] = (h >> 8) & 0xff;
                digest[i * 8 + 7] = h & 0xff;
            }
            break;
        }
            
        default:
            log_error("Unknown hash algorithm %d", state->algorithm);
            return -1;
    }
    
    return 0;
}

// One-shot hash function
int hash_data(hash_algorithm_t algorithm, const void *data, size_t length, uint8_t *digest) {
    hash_state_t state;
    
    if (hash_init(&state, algorithm) != 0) {
        return -1;
    }
    
    if (hash_update(&state, data, length) != 0) {
        return -1;
    }
    
    if (hash_final(&state, digest) != 0) {
        return -1;
    }
    
    return 0;
}

// Get digest length for algorithm
size_t hash_get_digest_length(hash_algorithm_t algorithm) {
    switch (algorithm) {
        case HASH_SHA256:
            return 32;  // 256 bits = 32 bytes
        case HASH_BLAKE2:
            return 32;  // Default BLAKE2b-256
        default:
            return 0;
    }
}

// Reset hash state for reuse
void hash_reset(hash_state_t *state) {
    if (!state) return;
    
    hash_algorithm_t algorithm = state->algorithm;
    hash_init(state, algorithm);
}

// Convert digest to hex string
void hash_to_hex(const uint8_t *digest, size_t length, char *hex_str) {
    if (!digest || !hex_str) return;
    
    for (size_t i = 0; i < length; i++) {
        sprintf(hex_str + (i * 2), "%02x", digest[i]);
    }
}

// Compare two digests for equality
int hash_compare(const uint8_t *a, const uint8_t *b, size_t length) {
    if (!a || !b) return 0;
    
    return memcmp(a, b, length) == 0;
}