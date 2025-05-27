#ifndef LOG_H
#define LOG_H

#include <stdarg.h>

// Log levels
typedef enum {
    LOG_TRACE,
    LOG_DEBUG,
    LOG_INFO,
    LOG_WARN,
    LOG_ERROR,
    LOG_FATAL
} log_level_t;

// Initialize logging system
int log_init(const char *log_file, log_level_t level);

// Clean up logging system
void log_cleanup(void);

// Set log level
void log_set_level(log_level_t level);

// Get current log level
log_level_t log_get_level(void);

// Log functions for different levels
void log_trace(const char *format, ...);
void log_debug(const char *format, ...);
void log_info(const char *format, ...);
void log_warn(const char *format, ...);
void log_error(const char *format, ...);
void log_fatal(const char *format, ...);

// Log with variable level
void log_log(log_level_t level, const char *format, ...);

// Log with variable level and va_list
void log_vlog(log_level_t level, const char *format, va_list args);

#endif // LOG_H