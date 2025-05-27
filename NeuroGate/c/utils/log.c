#include "log.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <stdarg.h>

// Static variables
static FILE *g_log_file = NULL;
static log_level_t g_log_level = LOG_INFO;
static int g_initialized = 0;

// Level strings
static const char *level_strings[] = {
    "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL"
};

// Level colors for console output
static const char *level_colors[] = {
    "\x1b[94m", "\x1b[36m", "\x1b[32m", "\x1b[33m", "\x1b[31m", "\x1b[35m"
};

// Reset color
static const char *reset_color = "\x1b[0m";

// Initialize logging system
int log_init(const char *log_file, log_level_t level) {
    if (g_initialized) {
        return 0;  // Already initialized
    }
    
    g_log_level = level;
    
    if (log_file != NULL) {
        g_log_file = fopen(log_file, "a");
        if (g_log_file == NULL) {
            fprintf(stderr, "Error: Could not open log file %s\n", log_file);
            return 0;
        }
    }
    
    g_initialized = 1;
    log_info("Logging system initialized");
    return 1;
}

// Clean up logging system
void log_cleanup(void) {
    if (!g_initialized) {
        return;
    }
    
    log_info("Logging system shutting down");
    
    if (g_log_file != NULL) {
        fclose(g_log_file);
        g_log_file = NULL;
    }
    
    g_initialized = 0;
}

// Set log level
void log_set_level(log_level_t level) {
    g_log_level = level;
    log_debug("Log level set to %s", level_strings[level]);
}

// Get current log level
log_level_t log_get_level(void) {
    return g_log_level;
}

// Internal logging function
static void log_internal(log_level_t level, const char *format, va_list args) {
    if (level < g_log_level) {
        return;  // Skip messages below current log level
    }
    
    // Get current time
    time_t now = time(NULL);
    struct tm *timeinfo = localtime(&now);
    char time_str[20];
    strftime(time_str, sizeof(time_str), "%Y-%m-%d %H:%M:%S", timeinfo);
    
    // Format for file output
    if (g_log_file != NULL) {
        fprintf(g_log_file, "[%s] [%s] ", time_str, level_strings[level]);
        vfprintf(g_log_file, format, args);
        fprintf(g_log_file, "\n");
        fflush(g_log_file);
    }
    
    // Format for console output with colors
    fprintf(stderr, "[%s] %s[%s]%s ", time_str, level_colors[level], 
            level_strings[level], reset_color);
    vfprintf(stderr, format, args);
    fprintf(stderr, "\n");
}

// Log with variable level
void log_log(log_level_t level, const char *format, ...) {
    if (!g_initialized && level >= LOG_WARN) {
        // Auto-initialize for warnings and errors
        log_init(NULL, LOG_INFO);
    }
    
    va_list args;
    va_start(args, format);
    log_internal(level, format, args);
    va_end(args);
    
    // Exit on fatal errors
    if (level == LOG_FATAL) {
        log_cleanup();
        exit(EXIT_FAILURE);
    }
}

// Log with variable level and va_list
void log_vlog(log_level_t level, const char *format, va_list args) {
    if (!g_initialized && level >= LOG_WARN) {
        // Auto-initialize for warnings and errors
        log_init(NULL, LOG_INFO);
    }
    
    log_internal(level, format, args);
    
    // Exit on fatal errors
    if (level == LOG_FATAL) {
        log_cleanup();
        exit(EXIT_FAILURE);
    }
}

// Log functions for different levels
void log_trace(const char *format, ...) {
    va_list args;
    va_start(args, format);
    log_vlog(LOG_TRACE, format, args);
    va_end(args);
}

void log_debug(const char *format, ...) {
    va_list args;
    va_start(args, format);
    log_vlog(LOG_DEBUG, format, args);
    va_end(args);
}

void log_info(const char *format, ...) {
    va_list args;
    va_start(args, format);
    log_vlog(LOG_INFO, format, args);
    va_end(args);
}

void log_warn(const char *format, ...) {
    va_list args;
    va_start(args, format);
    log_vlog(LOG_WARN, format, args);
    va_end(args);
}

void log_error(const char *format, ...) {
    va_list args;
    va_start(args, format);
    log_vlog(LOG_ERROR, format, args);
    va_end(args);
}

void log_fatal(const char *format, ...) {
    va_list args;
    va_start(args, format);
    log_vlog(LOG_FATAL, format, args);
    va_end(args);
    // log_vlog will exit the program
}