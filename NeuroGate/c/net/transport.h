#ifndef TRANSPORT_H
#define TRANSPORT_H

#include <stdint.h>
#include <stddef.h>

// Transport protocol message types
typedef enum {
    MSG_HANDSHAKE,       // Initial connection handshake
    MSG_DATA,            // Regular data message
    MSG_ACK,             // Acknowledgment
    MSG_NACK,            // Negative acknowledgment
    MSG_PING,            // Ping message to check connection
    MSG_PONG,            // Pong response to ping
    MSG_CLOSE            // Close connection
} message_type_t;

// Transport header structure
typedef struct {
    uint32_t magic;          // Magic number to identify protocol
    uint8_t version;         // Protocol version
    uint8_t type;            // Message type
    uint16_t flags;          // Message flags
    uint32_t seq_num;        // Sequence number
    uint32_t ack_num;        // Acknowledgment number
    uint32_t data_length;    // Length of data
    uint32_t checksum;       // Message checksum
} transport_header_t;

// Transport connection structure
typedef struct {
    int socket;              // Socket descriptor
    uint32_t remote_addr;    // Remote IP address
    uint16_t remote_port;    // Remote port
    uint32_t local_addr;     // Local IP address
    uint16_t local_port;     // Local port
    uint32_t seq_num;        // Current sequence number
    uint32_t ack_num;        // Current acknowledgment number
    uint16_t mtu;            // Maximum transmission unit
    uint8_t connected;       // Connection state
    uint8_t secure;          // Secure connection flag
    void *crypto_ctx;        // Cryptographic context
} transport_connection_t;

// Initialize transport layer
int transport_init(void);

// Clean up transport layer
void transport_cleanup(void);

// Create a new connection
transport_connection_t *transport_connect(const char *address, uint16_t port);

// Accept an incoming connection
transport_connection_t *transport_accept(int listen_socket);

// Close a connection
void transport_close(transport_connection_t *conn);

// Send data over a connection
int transport_send(transport_connection_t *conn, const void *data, size_t length);

// Receive data from a connection
int transport_receive(transport_connection_t *conn, void *buffer, size_t buffer_size);

// Set connection options
int transport_set_option(transport_connection_t *conn, int option, const void *value, size_t value_len);

// Get connection status
int transport_get_status(transport_connection_t *conn);

// Calculate checksum for message
uint32_t transport_calculate_checksum(const void *data, size_t length);

#endif // TRANSPORT_H