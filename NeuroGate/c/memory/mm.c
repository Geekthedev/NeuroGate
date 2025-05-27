#include "mm.h"
#include "../utils/log.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

// Memory block header structure
typedef struct memory_block {
    size_t size;                  // Size of the allocated block
    const char *file;             // Source file where allocation occurred
    int line;                     // Line number where allocation occurred
    struct memory_block *next;    // Pointer to next block in the list
    struct memory_block *prev;    // Pointer to previous block in the list
    unsigned int magic;           // Magic number to detect corruption
    char data[];                  // Start of user data (flexible array member)
} memory_block_t;

#define MEMORY_MAGIC 0xDEADBEEF
#define MEMORY_PADDING 16        // Padding for alignment

// Global memory tracking
static memory_block_t *g_memory_blocks = NULL;
static size_t g_total_memory = 0;
static size_t g_block_count = 0;
static int g_initialized = 0;

// Initialize memory management system
int mm_init(void) {
    if (g_initialized) {
        log_warn("Memory manager already initialized");
        return 0;
    }
    
    g_memory_blocks = NULL;
    g_total_memory = 0;
    g_block_count = 0;
    g_initialized = 1;
    
    log_info("Memory manager initialized");
    return 1;
}

// Clean up memory management system
void mm_cleanup(void) {
    if (!g_initialized) {
        return;
    }
    
    // Check for memory leaks
    if (g_block_count > 0) {
        log_warn("Memory leaks detected: %zu blocks, %zu bytes not freed", 
                 g_block_count, g_total_memory);
        
        // Print details of leaked blocks
        memory_block_t *block = g_memory_blocks;
        while (block != NULL) {
            log_debug("Leaked block: %zu bytes", block->size);
            memory_block_t *next = block->next;
            free(block);
            block = next;
        }
    } else {
        log_info("Memory manager cleaned up with no leaks");
    }
    
    g_memory_blocks = NULL;
    g_total_memory = 0;
    g_block_count = 0;
    g_initialized = 0;
}

// Allocate memory
void *mm_alloc(size_t size) {
    if (!g_initialized) {
        mm_init();
    }
    
    if (size == 0) {
        log_warn("Attempting to allocate zero bytes");
        return NULL;
    }
    
    // Allocate memory for block header and requested size
    memory_block_t *block = (memory_block_t *)malloc(sizeof(memory_block_t) + size);
    if (!block) {
        log_error("Memory allocation failed for %zu bytes", size);
        return NULL;
    }
    
    // Initialize block header
    block->size = size;
    block->file = "unknown";
    block->line = 0;
    block->magic = MEMORY_MAGIC;
    
    // Add block to linked list
    block->next = g_memory_blocks;
    block->prev = NULL;
    
    if (g_memory_blocks != NULL) {
        g_memory_blocks->prev = block;
    }
    
    g_memory_blocks = block;
    
    // Update statistics
    g_total_memory += size;
    g_block_count++;
    
    log_debug("Allocated %zu bytes at %p", size, block->data);
    
    // Return pointer to user data area
    return block->data;
}

// Reallocate memory
void *mm_realloc(void *ptr, size_t new_size) {
    // Handle NULL pointer as malloc
    if (ptr == NULL) {
        return mm_alloc(new_size);
    }
    
    // Handle zero size as free
    if (new_size == 0) {
        mm_free(ptr);
        return NULL;
    }
    
    // Get block header from user pointer
    memory_block_t *block = (memory_block_t *)((char *)ptr - offsetof(memory_block_t, data));
    
    // Validate block
    if (block->magic != MEMORY_MAGIC) {
        log_error("Invalid memory block in realloc at %p", ptr);
        return NULL;
    }
    
    // Update statistics
    g_total_memory -= block->size;
    
    // Allocate new block
    memory_block_t *new_block = (memory_block_t *)realloc(block, sizeof(memory_block_t) + new_size);
    if (!new_block) {
        // Reallocation failed, restore original statistics
        g_total_memory += block->size;
        log_error("Memory reallocation failed for %zu bytes", new_size);
        return NULL;
    }
    
    // Update block info
    new_block->size = new_size;
    
    // Update linked list if pointer changed
    if (new_block != block) {
        if (new_block->prev != NULL) {
            new_block->prev->next = new_block;
        } else {
            g_memory_blocks = new_block;
        }
        
        if (new_block->next != NULL) {
            new_block->next->prev = new_block;
        }
    }
    
    // Update statistics
    g_total_memory += new_size;
    
    log_debug("Reallocated from %zu to %zu bytes at %p", 
              block->size, new_size, new_block->data);
    
    return new_block->data;
}

// Free memory
void mm_free(void *ptr) {
    if (ptr == NULL) {
        return;
    }
    
    // Get block header from user pointer
    memory_block_t *block = (memory_block_t *)((char *)ptr - offsetof(memory_block_t, data));
    
    // Validate block
    if (block->magic != MEMORY_MAGIC) {
        log_error("Invalid memory block in free at %p", ptr);
        return;
    }
    
    // Remove block from linked list
    if (block->prev != NULL) {
        block->prev->next = block->next;
    } else {
        g_memory_blocks = block->next;
    }
    
    if (block->next != NULL) {
        block->next->prev = block->prev;
    }
    
    // Update statistics
    g_total_memory -= block->size;
    g_block_count--;
    
    log_debug("Freed %zu bytes at %p", block->size, ptr);
    
    // Clear magic number to detect double frees
    block->magic = 0;
    
    // Free the memory
    free(block);
}

// Get current memory usage
size_t mm_get_used_memory(void) {
    return g_total_memory;
}

// Get number of allocated blocks
size_t mm_get_allocated_blocks(void) {
    return g_block_count;
}

// Print memory usage statistics
void mm_debug_print_stats(void) {
    log_info("Memory usage: %zu bytes in %zu blocks", g_total_memory, g_block_count);
    
    // Optionally print detailed block information
    if (log_get_level() <= LOG_DEBUG) {
        memory_block_t *block = g_memory_blocks;
        while (block != NULL) {
            log_debug("Block at %p: %zu bytes", block->data, block->size);
            block = block->next;
        }
    }
}

// Check for memory leaks
int mm_check_leaks(void) {
    if (g_block_count > 0) {
        log_warn("Memory leaks detected: %zu blocks, %zu bytes not freed", 
                 g_block_count, g_total_memory);
        return 1;
    }
    return 0;
}