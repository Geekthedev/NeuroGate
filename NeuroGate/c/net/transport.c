#include "transport.h"
#include "../utils/log.h"
#include "../memory/mm.h"
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>

// Protocol constants
#define TRANSPORT_MAGIC 0x4E474154  // "NGAT" in ASCII
#define TRANSPORT_VERSION 1
#define TRANSPORT_HEADER_SIZE sizeof(transport_header_t)
#define TRANSPORT_MAX_RETRIES 5
#define TRANSPORT_TIMEOUT_MS 1000
#define TRANSPORT_DEFAULT_MTU 1500

// Protocol flags
#define FLAG_ENCRYPTED 0x0001
#define FLAG_COMPRESSED 0x0002
#define FLAG_FRAGMENTED 0x0004
#define FLAG_LAST_FRAGMENT 0x0008
#define FLAG_URGENT 0x0010
#define FLAG_RELIABLE 0x0020

// Initialize transport layer
int transport_init(void) {
    log_info("Transport layer initialized");
    return 1;
}

// Clean up transport layer
void transport_cleanup(void) {
    log_info("Transport layer cleaned up");
}

// Create a new connection
transport_connection_t *transport_connect(const char *address, uint16_t port) {
    if (!address) {
        log_error("Invalid address for connection");
        return NULL;
    }
    
    // Allocate connection structure
    transport_connection_t *conn = (transport_connection_t *)mm_alloc(sizeof(transport_connection_t));
    if (!conn) {
        log_error("Failed to allocate memory for connection");
        return NULL;
    }
    
    // Initialize connection
    memset(conn, 0, sizeof(transport_connection_t));
    conn->remote_port = port;
    conn->seq_num = (uint32_t)rand();  // Random initial sequence number
    conn->ack_num = 0;
    conn->mtu = TRANSPORT_DEFAULT_MTU;
    
    // This is a simplified version that doesn't actually establish a socket connection
    // In a real implementation, this would set up a socket and connect to the address
    conn->socket = -1;  // Placeholder
    
    // For demonstration purposes, we'll just set the connection state
    conn->connected = 1;
    
    log_info("Created connection to %s:%u", address, port);
    return conn;
}

// Accept an incoming connection
transport_connection_t *transport_accept(int listen_socket) {
    // In a real implementation, this would accept a connection on the listen socket
    
    // Allocate connection structure
    transport_connection_t *conn = (transport_connection_t *)mm_alloc(sizeof(transport_connection_t));
    if (!conn) {
        log_error("Failed to allocate memory for incoming connection");
        return NULL;
    }
    
    // Initialize connection
    memset(conn, 0, sizeof(transport_connection_t));
    conn->seq_num = (uint32_t)rand();  // Random initial sequence number
    conn->ack_num = 0;
    conn->mtu = TRANSPORT_DEFAULT_MTU;
    
    // This is a simplified version
    conn->socket = -1;  // Placeholder
    conn->connected = 1;
    
    log_info("Accepted incoming connection");
    return conn;
}

// Close a connection
void transport_close(transport_connection_t *conn) {
    if (!conn) return;
    
    // If there's an actual socket, we would close it here
    if (conn->socket != -1) {
        // close(conn->socket);
    }
    
    // Free the connection structure
    mm_free(conn);
    
    log_info("Closed connection");
}

// Send data over a connection
int transport_send(transport_connection_t *conn, const void *data, size_t length) {
    if (!conn || !data || length == 0) {
        log_error("Invalid parameters for send");
        return -1;
    }
    
    // Check connection state
    if (!conn->connected) {
        log_error("Attempting to send on a closed connection");
        return -1;
    }
    
    // Create message header
    transport_header_t header;
    header.magic = TRANSPORT_MAGIC;
    header.version = TRANSPORT_VERSION;
    header.type = MSG_DATA;
    header.flags = 0;
    
    // Set reliable flag for data messages
    header.flags |= FLAG_RELIABLE;
    
    header.seq_num = conn->seq_num++;
    header.ack_num = conn->ack_num;
    header.data_length = length;
    
    // Calculate checksum
    header.checksum = transport_calculate_checksum(data, length);
    
    // In a real implementation, we would send the header and data over the socket
    // For this simplified version, we'll just log the send
    log_debug("Sent %zu bytes, seq=%u, ack=%u", length, header.seq_num, header.ack_num);
    
    return length;
}

// Receive data from a connection
int transport_receive(transport_connection_t *conn, void *buffer, size_t buffer_size) {
    if (!conn || !buffer || buffer_size == 0) {
        log_error("Invalid parameters for receive");
        return -1;
    }
    
    // Check connection state
    if (!conn->connected) {
        log_error("Attempting to receive on a closed connection");
        return -1;
    }
    
    // In a real implementation, we would:
    // 1. Receive the header
    // 2. Validate the header (magic, version, checksum)
    // 3. Receive the data
    // 4. Update ack_num
    // 5. Send an ACK if needed
    
    // For this simplified version, we'll just simulate receiving no data
    log_debug("No data available to receive");
    
    return 0;  // No data received
}

// Set connection options
int transport_set_option(transport_connection_t *conn, int option, const void *value, size_t value_len) {
    if (!conn || !value) {
        log_error("Invalid parameters for set option");
        return -1;
    }
    
    // Handle different options
    switch (option) {
        case 1:  // Example: Set MTU
            if (value_len != sizeof(uint16_t)) {
                log_error("Invalid value size for MTU option");
                return -1;
            }
            conn->mtu = *(const uint16_t *)value;
            log_debug("Set MTU to %u", conn->mtu);
            break;
            
        case 2:  // Example: Set secure mode
            if (value_len != sizeof(uint8_t)) {
                log_error("Invalid value size for secure mode option");
                return -1;
            }
            conn->secure = *(const uint8_t *)value;
            log_debug("Set secure mode to %u", conn->secure);
            break;
            
        default:
            log_error("Unknown option %d", option);
            return -1;
    }
    
    return 0;
}

// Get connection status
int transport_get_status(transport_connection_t *conn) {
    if (!conn) {
        return -1;
    }
    
    return conn->connected ? 1 : 0;
}

// Calculate simple checksum for message
uint32_t transport_calculate_checksum(const void *data, size_t length) {
    if (!data || length == 0) {
        return 0;
    }
    
    const uint8_t *bytes = (const uint8_t *)data;
    uint32_t checksum = 0;
    
    // Simple additive checksum with byte rotation
    for (size_t i = 0; i < length; i++) {
        checksum = (checksum << 1) | (checksum >> 31);  // Rotate left by 1
        checksum += bytes[i];
    }
    
    return checksum;
}