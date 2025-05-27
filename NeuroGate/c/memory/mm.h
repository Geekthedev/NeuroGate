#ifndef MEMORY_MANAGER_H
#define MEMORY_MANAGER_H

#include <stddef.h>

// Initialize memory management system
int mm_init(void);

// Clean up memory management system
void mm_cleanup(void);

// Allocate memory
void *mm_alloc(size_t size);

// Reallocate memory
void *mm_realloc(void *ptr, size_t new_size);

// Free memory
void mm_free(void *ptr);

// Get current memory usage statistics
size_t mm_get_used_memory(void);
size_t mm_get_allocated_blocks(void);

// Memory debugging
void mm_debug_print_stats(void);
int mm_check_leaks(void);

#endif // MEMORY_MANAGER_H