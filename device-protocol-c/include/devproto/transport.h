/**
 * @file transport.h
 * @brief Transport abstraction layer
 *
 * Provides a common interface for different transport mechanisms:
 * - Serial/UART (termios)
 * - TCP sockets
 */

#ifndef DEVPROTO_TRANSPORT_H
#define DEVPROTO_TRANSPORT_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Forward declaration */
typedef struct devproto_transport devproto_transport_t;

/**
 * Transport type identifiers
 */
typedef enum {
    DEVPROTO_TRANSPORT_SERIAL = 1,
    DEVPROTO_TRANSPORT_TCP    = 2
} devproto_transport_type_t;

/**
 * Transport operations vtable
 */
typedef struct {
    int  (*open)(devproto_transport_t *t);
    void (*close)(devproto_transport_t *t);
    int  (*send)(devproto_transport_t *t, const uint8_t *data, size_t len);
    int  (*recv)(devproto_transport_t *t, uint8_t *data, size_t len, int timeout_ms);
    int  (*available)(devproto_transport_t *t);
    int  (*flush)(devproto_transport_t *t);
} devproto_transport_ops_t;

/**
 * Transport base structure
 */
struct devproto_transport {
    devproto_transport_type_t type;     /* Transport type */
    const devproto_transport_ops_t *ops; /* Operations vtable */
    void *priv;                         /* Transport-specific private data */
    int   fd;                           /* File descriptor (if applicable) */
    int   is_open;                      /* Connection state */
};

/**
 * Create serial transport
 * @param device    Device path (e.g., "/dev/ttyUSB0")
 * @param baudrate  Baud rate (default: 115200)
 * @return          Transport handle, or NULL on error
 */
devproto_transport_t *devproto_transport_serial_create(const char *device, int baudrate);

/**
 * Create TCP transport
 * @param host  Host address
 * @param port  Port number
 * @return      Transport handle, or NULL on error
 */
devproto_transport_t *devproto_transport_tcp_create(const char *host, int port);

/**
 * Destroy transport and free resources
 * @param t  Transport handle
 */
void devproto_transport_destroy(devproto_transport_t *t);

/**
 * Open transport connection
 * @param t  Transport handle
 * @return   0 on success, -1 on error
 */
static inline int devproto_transport_open(devproto_transport_t *t) {
    if (!t || !t->ops || !t->ops->open) return -1;
    return t->ops->open(t);
}

/**
 * Close transport connection
 * @param t  Transport handle
 */
static inline void devproto_transport_close(devproto_transport_t *t) {
    if (t && t->ops && t->ops->close) {
        t->ops->close(t);
    }
}

/**
 * Send data over transport
 * @param t     Transport handle
 * @param data  Data to send
 * @param len   Data length
 * @return      Number of bytes sent, or -1 on error
 */
static inline int devproto_transport_send(devproto_transport_t *t,
                                          const uint8_t *data, size_t len) {
    if (!t || !t->ops || !t->ops->send) return -1;
    return t->ops->send(t, data, len);
}

/**
 * Receive data from transport
 * @param t           Transport handle
 * @param data        Buffer for received data
 * @param len         Buffer size
 * @param timeout_ms  Timeout in milliseconds (-1 for blocking)
 * @return            Number of bytes received, 0 on timeout, -1 on error
 */
static inline int devproto_transport_recv(devproto_transport_t *t,
                                          uint8_t *data, size_t len,
                                          int timeout_ms) {
    if (!t || !t->ops || !t->ops->recv) return -1;
    return t->ops->recv(t, data, len, timeout_ms);
}

/**
 * Get number of bytes available for reading
 * @param t  Transport handle
 * @return   Number of bytes available, or -1 on error
 */
static inline int devproto_transport_available(devproto_transport_t *t) {
    if (!t || !t->ops || !t->ops->available) return -1;
    return t->ops->available(t);
}

/**
 * Flush pending data
 * @param t  Transport handle
 * @return   0 on success, -1 on error
 */
static inline int devproto_transport_flush(devproto_transport_t *t) {
    if (!t || !t->ops || !t->ops->flush) return -1;
    return t->ops->flush(t);
}

/**
 * Check if transport is open
 */
static inline int devproto_transport_is_open(const devproto_transport_t *t) {
    return t && t->is_open;
}

#ifdef __cplusplus
}
#endif

#endif /* DEVPROTO_TRANSPORT_H */
