/**
 * @file tls.h
 * @brief TLS transport layer
 *
 * Provides TLS/SSL encrypted transport using mbedTLS.
 * Supports both client and server modes with mutual authentication.
 *
 * Build with DEVPROTO_TLS_ENABLE to enable TLS support.
 * Requires mbedTLS library (https://github.com/Mbed-TLS/mbedtls)
 *
 * Example usage:
 * @code
 *   devproto_tls_config_t cfg = {
 *       .host = "192.168.1.100",
 *       .port = 9443,
 *       .ca_cert_path = "/etc/certs/ca.crt",
 *       .client_cert_path = "/etc/certs/client.crt",
 *       .client_key_path = "/etc/certs/client.key",
 *       .verify_server = 1
 *   };
 *   devproto_transport_t *tls = devproto_transport_tls_create(&cfg);
 *   if (tls && devproto_transport_open(tls) == 0) {
 *       // Use like any other transport
 *   }
 * @endcode
 */

#ifndef DEVPROTO_TLS_H
#define DEVPROTO_TLS_H

#include <stdint.h>
#include <stddef.h>
#include "transport.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * TLS transport type identifier (extends devproto_transport_type_t)
 */
#define DEVPROTO_TRANSPORT_TLS 3

/**
 * TLS version constants
 */
typedef enum {
    DEVPROTO_TLS_VERSION_1_2 = 0x0303,
    DEVPROTO_TLS_VERSION_1_3 = 0x0304
} devproto_tls_version_t;

/**
 * TLS authentication mode
 */
typedef enum {
    DEVPROTO_TLS_AUTH_NONE     = 0,  /* No authentication (insecure) */
    DEVPROTO_TLS_AUTH_OPTIONAL = 1,  /* Optional client authentication */
    DEVPROTO_TLS_AUTH_REQUIRED = 2   /* Required mutual authentication */
} devproto_tls_auth_mode_t;

/**
 * TLS connection state
 */
typedef enum {
    DEVPROTO_TLS_STATE_INIT        = 0,
    DEVPROTO_TLS_STATE_HANDSHAKE   = 1,
    DEVPROTO_TLS_STATE_CONNECTED   = 2,
    DEVPROTO_TLS_STATE_CLOSING     = 3,
    DEVPROTO_TLS_STATE_CLOSED      = 4,
    DEVPROTO_TLS_STATE_ERROR       = -1
} devproto_tls_state_t;

/**
 * TLS error codes
 */
typedef enum {
    DEVPROTO_TLS_OK                  = 0,
    DEVPROTO_TLS_ERR_MEMORY          = -1,
    DEVPROTO_TLS_ERR_CERT_LOAD       = -2,
    DEVPROTO_TLS_ERR_KEY_LOAD        = -3,
    DEVPROTO_TLS_ERR_CA_LOAD         = -4,
    DEVPROTO_TLS_ERR_CONNECT         = -5,
    DEVPROTO_TLS_ERR_HANDSHAKE       = -6,
    DEVPROTO_TLS_ERR_VERIFY          = -7,
    DEVPROTO_TLS_ERR_SEND            = -8,
    DEVPROTO_TLS_ERR_RECV            = -9,
    DEVPROTO_TLS_ERR_CLOSED          = -10,
    DEVPROTO_TLS_ERR_WANT_READ       = -11,
    DEVPROTO_TLS_ERR_WANT_WRITE      = -12,
    DEVPROTO_TLS_ERR_INVALID_CONFIG  = -13,
    DEVPROTO_TLS_ERR_NOT_SUPPORTED   = -14
} devproto_tls_error_t;

/**
 * TLS configuration structure
 */
typedef struct {
    /* Connection settings */
    const char *host;              /* Server hostname or IP */
    int         port;              /* Server port */

    /* Certificate paths (PEM format) */
    const char *ca_cert_path;      /* CA certificate for server verification */
    const char *client_cert_path;  /* Client certificate for mutual TLS */
    const char *client_key_path;   /* Client private key */

    /* In-memory certificates (alternative to paths) */
    const uint8_t *ca_cert;        /* CA certificate data */
    size_t         ca_cert_len;    /* CA certificate length */
    const uint8_t *client_cert;    /* Client certificate data */
    size_t         client_cert_len;/* Client certificate length */
    const uint8_t *client_key;     /* Client key data */
    size_t         client_key_len; /* Client key length */

    /* TLS settings */
    devproto_tls_version_t min_version;  /* Minimum TLS version (default: 1.2) */
    devproto_tls_version_t max_version;  /* Maximum TLS version (default: 1.3) */
    devproto_tls_auth_mode_t auth_mode;  /* Authentication mode */

    /* Verification settings */
    int          verify_server;    /* Verify server certificate (default: 1) */
    const char  *expected_cn;      /* Expected server Common Name (optional) */

    /* Timeouts */
    int          handshake_timeout_ms;  /* Handshake timeout (default: 30000) */
    int          read_timeout_ms;       /* Read timeout (default: 5000) */
    int          write_timeout_ms;      /* Write timeout (default: 5000) */

    /* Debug callback (optional) */
    void (*debug_callback)(int level, const char *file, int line, const char *msg);
} devproto_tls_config_t;

/**
 * TLS connection info (after successful handshake)
 */
typedef struct {
    devproto_tls_version_t version;      /* Negotiated TLS version */
    const char *cipher_suite;            /* Negotiated cipher suite name */
    const char *server_cn;               /* Server certificate CN */
    int         verify_result;           /* Certificate verification result */
    int         session_resumed;         /* Session was resumed */
} devproto_tls_info_t;

/**
 * Initialize default TLS configuration
 * @param cfg  Configuration structure to initialize
 */
void devproto_tls_config_init(devproto_tls_config_t *cfg);

#ifdef DEVPROTO_TLS_ENABLE

/**
 * Create TLS transport
 * @param cfg  TLS configuration
 * @return     Transport handle, or NULL on error
 *
 * Note: The transport is created but not connected.
 * Call devproto_transport_open() to establish the connection.
 */
devproto_transport_t *devproto_transport_tls_create(const devproto_tls_config_t *cfg);

/**
 * Get TLS connection info
 * @param t     Transport handle
 * @param info  Output info structure
 * @return      0 on success, error code on failure
 */
int devproto_tls_get_info(devproto_transport_t *t, devproto_tls_info_t *info);

/**
 * Get TLS connection state
 * @param t  Transport handle
 * @return   Current TLS state
 */
devproto_tls_state_t devproto_tls_get_state(devproto_transport_t *t);

/**
 * Get last TLS error code
 * @param t  Transport handle
 * @return   Last error code
 */
devproto_tls_error_t devproto_tls_get_error(devproto_transport_t *t);

/**
 * Get human-readable error message
 * @param err  Error code
 * @return     Error message string
 */
const char *devproto_tls_strerror(devproto_tls_error_t err);

/**
 * Force TLS session renegotiation
 * @param t  Transport handle
 * @return   0 on success, error code on failure
 */
int devproto_tls_renegotiate(devproto_transport_t *t);

/**
 * Check if TLS support is available
 * @return  1 if TLS is available, 0 otherwise
 */
int devproto_tls_available(void);

/**
 * Get mbedTLS version string
 * @return  Version string (e.g., "3.4.0")
 */
const char *devproto_tls_version(void);

#else /* !DEVPROTO_TLS_ENABLE */

/* Stub implementations when TLS is not enabled */
static inline devproto_transport_t *devproto_transport_tls_create(
    const devproto_tls_config_t *cfg) {
    (void)cfg;
    return NULL;
}

static inline int devproto_tls_get_info(devproto_transport_t *t,
                                         devproto_tls_info_t *info) {
    (void)t; (void)info;
    return DEVPROTO_TLS_ERR_NOT_SUPPORTED;
}

static inline devproto_tls_state_t devproto_tls_get_state(devproto_transport_t *t) {
    (void)t;
    return DEVPROTO_TLS_STATE_ERROR;
}

static inline devproto_tls_error_t devproto_tls_get_error(devproto_transport_t *t) {
    (void)t;
    return DEVPROTO_TLS_ERR_NOT_SUPPORTED;
}

static inline const char *devproto_tls_strerror(devproto_tls_error_t err) {
    (void)err;
    return "TLS not supported (build with DEVPROTO_TLS_ENABLE)";
}

static inline int devproto_tls_renegotiate(devproto_transport_t *t) {
    (void)t;
    return DEVPROTO_TLS_ERR_NOT_SUPPORTED;
}

static inline int devproto_tls_available(void) {
    return 0;
}

static inline const char *devproto_tls_version(void) {
    return "disabled";
}

#endif /* DEVPROTO_TLS_ENABLE */

#ifdef __cplusplus
}
#endif

#endif /* DEVPROTO_TLS_H */
