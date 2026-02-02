/**
 * @file transport_tls.c
 * @brief TLS transport implementation using mbedTLS
 *
 * Build with DEVPROTO_TLS_ENABLE and link against mbedTLS to enable.
 * Without DEVPROTO_TLS_ENABLE, this file compiles to stubs.
 */

#include "devproto/tls.h"
#include "devproto/transport.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

/* Initialize default TLS configuration */
void devproto_tls_config_init(devproto_tls_config_t *cfg) {
    if (!cfg) return;
    memset(cfg, 0, sizeof(*cfg));
    cfg->min_version = DEVPROTO_TLS_VERSION_1_2;
    cfg->max_version = DEVPROTO_TLS_VERSION_1_3;
    cfg->auth_mode = DEVPROTO_TLS_AUTH_REQUIRED;
    cfg->verify_server = 1;
    cfg->handshake_timeout_ms = 30000;
    cfg->read_timeout_ms = 5000;
    cfg->write_timeout_ms = 5000;
}

#ifdef DEVPROTO_TLS_ENABLE

/*
 * Full TLS implementation requires mbedTLS
 *
 * Build requirements:
 *   - mbedTLS library (libmbedtls, libmbedcrypto, libmbedx509)
 *   - Compile with: -DDEVPROTO_TLS_ENABLE
 *   - Link with: -lmbedtls -lmbedcrypto -lmbedx509
 *
 * For MIPS cross-compilation:
 *   1. Download mbedTLS source
 *   2. Cross-compile: make CC=mips-linux-gnu-gcc
 *   3. Install to sysroot or specify include/lib paths
 */

#include <mbedtls/ssl.h>
#include <mbedtls/net_sockets.h>
#include <mbedtls/entropy.h>
#include <mbedtls/ctr_drbg.h>
#include <mbedtls/error.h>
#include <mbedtls/debug.h>
#include <mbedtls/version.h>

/* TLS transport private data */
typedef struct {
    devproto_tls_config_t config;
    devproto_tls_state_t state;
    devproto_tls_error_t last_error;

    mbedtls_net_context net_ctx;
    mbedtls_ssl_context ssl_ctx;
    mbedtls_ssl_config ssl_conf;
    mbedtls_x509_crt ca_cert;
    mbedtls_x509_crt client_cert;
    mbedtls_pk_context client_key;
    mbedtls_entropy_context entropy;
    mbedtls_ctr_drbg_context ctr_drbg;

    char server_cn[256];
    int verify_result;
} tls_priv_t;

/* Forward declarations */
static int tls_open(devproto_transport_t *t);
static void tls_close(devproto_transport_t *t);
static int tls_send(devproto_transport_t *t, const uint8_t *data, size_t len);
static int tls_recv(devproto_transport_t *t, uint8_t *data, size_t len, int timeout_ms);
static int tls_available(devproto_transport_t *t);
static int tls_flush(devproto_transport_t *t);

static const devproto_transport_ops_t tls_ops = {
    .open = tls_open,
    .close = tls_close,
    .send = tls_send,
    .recv = tls_recv,
    .available = tls_available,
    .flush = tls_flush
};

/* Debug callback wrapper */
static void tls_debug_callback(void *ctx, int level,
                                const char *file, int line,
                                const char *str) {
    tls_priv_t *priv = (tls_priv_t *)ctx;
    if (priv && priv->config.debug_callback) {
        priv->config.debug_callback(level, file, line, str);
    }
}

devproto_transport_t *devproto_transport_tls_create(const devproto_tls_config_t *cfg) {
    if (!cfg || !cfg->host || cfg->port <= 0) {
        return NULL;
    }

    devproto_transport_t *t = calloc(1, sizeof(*t));
    if (!t) return NULL;

    tls_priv_t *priv = calloc(1, sizeof(*priv));
    if (!priv) {
        free(t);
        return NULL;
    }

    /* Copy configuration */
    memcpy(&priv->config, cfg, sizeof(*cfg));
    priv->state = DEVPROTO_TLS_STATE_INIT;
    priv->last_error = DEVPROTO_TLS_OK;

    /* Initialize mbedTLS structures */
    mbedtls_net_init(&priv->net_ctx);
    mbedtls_ssl_init(&priv->ssl_ctx);
    mbedtls_ssl_config_init(&priv->ssl_conf);
    mbedtls_x509_crt_init(&priv->ca_cert);
    mbedtls_x509_crt_init(&priv->client_cert);
    mbedtls_pk_init(&priv->client_key);
    mbedtls_entropy_init(&priv->entropy);
    mbedtls_ctr_drbg_init(&priv->ctr_drbg);

    /* Seed the random number generator */
    int ret = mbedtls_ctr_drbg_seed(&priv->ctr_drbg, mbedtls_entropy_func,
                                     &priv->entropy, NULL, 0);
    if (ret != 0) {
        priv->last_error = DEVPROTO_TLS_ERR_MEMORY;
        goto error;
    }

    /* Setup SSL defaults */
    ret = mbedtls_ssl_config_defaults(&priv->ssl_conf,
                                       MBEDTLS_SSL_IS_CLIENT,
                                       MBEDTLS_SSL_TRANSPORT_STREAM,
                                       MBEDTLS_SSL_PRESET_DEFAULT);
    if (ret != 0) {
        priv->last_error = DEVPROTO_TLS_ERR_INVALID_CONFIG;
        goto error;
    }

    /* Set TLS version range */
    mbedtls_ssl_conf_min_version(&priv->ssl_conf,
        MBEDTLS_SSL_MAJOR_VERSION_3,
        (cfg->min_version == DEVPROTO_TLS_VERSION_1_3) ?
            MBEDTLS_SSL_MINOR_VERSION_4 : MBEDTLS_SSL_MINOR_VERSION_3);
    mbedtls_ssl_conf_max_version(&priv->ssl_conf,
        MBEDTLS_SSL_MAJOR_VERSION_3,
        (cfg->max_version == DEVPROTO_TLS_VERSION_1_3) ?
            MBEDTLS_SSL_MINOR_VERSION_4 : MBEDTLS_SSL_MINOR_VERSION_3);

    /* Configure RNG */
    mbedtls_ssl_conf_rng(&priv->ssl_conf, mbedtls_ctr_drbg_random, &priv->ctr_drbg);

    /* Configure debug if callback provided */
    if (cfg->debug_callback) {
        mbedtls_ssl_conf_dbg(&priv->ssl_conf, tls_debug_callback, priv);
        mbedtls_debug_set_threshold(4);
    }

    /* Load CA certificate */
    if (cfg->ca_cert_path) {
        ret = mbedtls_x509_crt_parse_file(&priv->ca_cert, cfg->ca_cert_path);
        if (ret != 0) {
            priv->last_error = DEVPROTO_TLS_ERR_CA_LOAD;
            goto error;
        }
    } else if (cfg->ca_cert && cfg->ca_cert_len > 0) {
        ret = mbedtls_x509_crt_parse(&priv->ca_cert, cfg->ca_cert, cfg->ca_cert_len);
        if (ret != 0) {
            priv->last_error = DEVPROTO_TLS_ERR_CA_LOAD;
            goto error;
        }
    }

    /* Load client certificate for mutual TLS */
    if (cfg->client_cert_path && cfg->client_key_path) {
        ret = mbedtls_x509_crt_parse_file(&priv->client_cert, cfg->client_cert_path);
        if (ret != 0) {
            priv->last_error = DEVPROTO_TLS_ERR_CERT_LOAD;
            goto error;
        }

        ret = mbedtls_pk_parse_keyfile(&priv->client_key, cfg->client_key_path, NULL);
        if (ret != 0) {
            priv->last_error = DEVPROTO_TLS_ERR_KEY_LOAD;
            goto error;
        }

        mbedtls_ssl_conf_own_cert(&priv->ssl_conf, &priv->client_cert, &priv->client_key);
    }

    /* Configure certificate verification */
    mbedtls_ssl_conf_ca_chain(&priv->ssl_conf, &priv->ca_cert, NULL);
    mbedtls_ssl_conf_authmode(&priv->ssl_conf,
        cfg->verify_server ? MBEDTLS_SSL_VERIFY_REQUIRED : MBEDTLS_SSL_VERIFY_NONE);

    /* Setup SSL context */
    ret = mbedtls_ssl_setup(&priv->ssl_ctx, &priv->ssl_conf);
    if (ret != 0) {
        priv->last_error = DEVPROTO_TLS_ERR_INVALID_CONFIG;
        goto error;
    }

    /* Set hostname for SNI */
    if (cfg->expected_cn) {
        mbedtls_ssl_set_hostname(&priv->ssl_ctx, cfg->expected_cn);
    } else {
        mbedtls_ssl_set_hostname(&priv->ssl_ctx, cfg->host);
    }

    t->type = DEVPROTO_TRANSPORT_TLS;
    t->ops = &tls_ops;
    t->priv = priv;
    t->fd = -1;
    t->is_open = 0;

    return t;

error:
    mbedtls_net_free(&priv->net_ctx);
    mbedtls_ssl_free(&priv->ssl_ctx);
    mbedtls_ssl_config_free(&priv->ssl_conf);
    mbedtls_x509_crt_free(&priv->ca_cert);
    mbedtls_x509_crt_free(&priv->client_cert);
    mbedtls_pk_free(&priv->client_key);
    mbedtls_entropy_free(&priv->entropy);
    mbedtls_ctr_drbg_free(&priv->ctr_drbg);
    free(priv);
    free(t);
    return NULL;
}

static int tls_open(devproto_transport_t *t) {
    if (!t || !t->priv) return -1;
    tls_priv_t *priv = (tls_priv_t *)t->priv;

    if (t->is_open) return 0;

    priv->state = DEVPROTO_TLS_STATE_HANDSHAKE;

    /* Connect TCP */
    char port_str[16];
    snprintf(port_str, sizeof(port_str), "%d", priv->config.port);

    int ret = mbedtls_net_connect(&priv->net_ctx, priv->config.host, port_str,
                                   MBEDTLS_NET_PROTO_TCP);
    if (ret != 0) {
        priv->last_error = DEVPROTO_TLS_ERR_CONNECT;
        priv->state = DEVPROTO_TLS_STATE_ERROR;
        return -1;
    }

    /* Set timeout */
    mbedtls_ssl_conf_read_timeout(&priv->ssl_conf, priv->config.handshake_timeout_ms);

    /* Set I/O functions */
    mbedtls_ssl_set_bio(&priv->ssl_ctx, &priv->net_ctx,
                         mbedtls_net_send, mbedtls_net_recv, mbedtls_net_recv_timeout);

    /* Perform TLS handshake */
    while ((ret = mbedtls_ssl_handshake(&priv->ssl_ctx)) != 0) {
        if (ret != MBEDTLS_ERR_SSL_WANT_READ && ret != MBEDTLS_ERR_SSL_WANT_WRITE) {
            priv->last_error = DEVPROTO_TLS_ERR_HANDSHAKE;
            priv->state = DEVPROTO_TLS_STATE_ERROR;
            mbedtls_net_free(&priv->net_ctx);
            return -1;
        }
    }

    /* Verify server certificate */
    priv->verify_result = mbedtls_ssl_get_verify_result(&priv->ssl_ctx);
    if (priv->config.verify_server && priv->verify_result != 0) {
        priv->last_error = DEVPROTO_TLS_ERR_VERIFY;
        priv->state = DEVPROTO_TLS_STATE_ERROR;
        mbedtls_ssl_close_notify(&priv->ssl_ctx);
        mbedtls_net_free(&priv->net_ctx);
        return -1;
    }

    /* Store server CN */
    const mbedtls_x509_crt *peer_cert = mbedtls_ssl_get_peer_cert(&priv->ssl_ctx);
    if (peer_cert) {
        mbedtls_x509_dn_gets(priv->server_cn, sizeof(priv->server_cn),
                              &peer_cert->subject);
    }

    /* Set read timeout for normal operations */
    mbedtls_ssl_conf_read_timeout(&priv->ssl_conf, priv->config.read_timeout_ms);

    t->fd = priv->net_ctx.fd;
    t->is_open = 1;
    priv->state = DEVPROTO_TLS_STATE_CONNECTED;

    return 0;
}

static void tls_close(devproto_transport_t *t) {
    if (!t || !t->priv) return;
    tls_priv_t *priv = (tls_priv_t *)t->priv;

    if (!t->is_open) return;

    priv->state = DEVPROTO_TLS_STATE_CLOSING;

    /* Send close notify */
    mbedtls_ssl_close_notify(&priv->ssl_ctx);

    /* Close network connection */
    mbedtls_net_free(&priv->net_ctx);

    t->fd = -1;
    t->is_open = 0;
    priv->state = DEVPROTO_TLS_STATE_CLOSED;
}

static int tls_send(devproto_transport_t *t, const uint8_t *data, size_t len) {
    if (!t || !t->priv || !data) return -1;
    tls_priv_t *priv = (tls_priv_t *)t->priv;

    if (!t->is_open) {
        priv->last_error = DEVPROTO_TLS_ERR_CLOSED;
        return -1;
    }

    int ret;
    size_t written = 0;

    while (written < len) {
        ret = mbedtls_ssl_write(&priv->ssl_ctx, data + written, len - written);
        if (ret > 0) {
            written += ret;
        } else if (ret == MBEDTLS_ERR_SSL_WANT_WRITE) {
            continue;
        } else {
            priv->last_error = DEVPROTO_TLS_ERR_SEND;
            return -1;
        }
    }

    return (int)written;
}

static int tls_recv(devproto_transport_t *t, uint8_t *data, size_t len, int timeout_ms) {
    if (!t || !t->priv || !data) return -1;
    tls_priv_t *priv = (tls_priv_t *)t->priv;

    if (!t->is_open) {
        priv->last_error = DEVPROTO_TLS_ERR_CLOSED;
        return -1;
    }

    /* Set timeout for this operation */
    if (timeout_ms >= 0) {
        mbedtls_ssl_conf_read_timeout(&priv->ssl_conf, timeout_ms);
    }

    int ret = mbedtls_ssl_read(&priv->ssl_ctx, data, len);

    if (ret > 0) {
        return ret;
    } else if (ret == 0 || ret == MBEDTLS_ERR_SSL_PEER_CLOSE_NOTIFY) {
        priv->last_error = DEVPROTO_TLS_ERR_CLOSED;
        t->is_open = 0;
        priv->state = DEVPROTO_TLS_STATE_CLOSED;
        return -1;
    } else if (ret == MBEDTLS_ERR_SSL_WANT_READ) {
        return 0;  /* Timeout */
    } else if (ret == MBEDTLS_ERR_SSL_TIMEOUT) {
        return 0;  /* Timeout */
    } else {
        priv->last_error = DEVPROTO_TLS_ERR_RECV;
        return -1;
    }
}

static int tls_available(devproto_transport_t *t) {
    if (!t || !t->priv || !t->is_open) return -1;
    tls_priv_t *priv = (tls_priv_t *)t->priv;

    return mbedtls_ssl_get_bytes_avail(&priv->ssl_ctx);
}

static int tls_flush(devproto_transport_t *t) {
    /* TLS doesn't buffer at the application layer */
    (void)t;
    return 0;
}

int devproto_tls_get_info(devproto_transport_t *t, devproto_tls_info_t *info) {
    if (!t || !t->priv || !info) return DEVPROTO_TLS_ERR_INVALID_CONFIG;
    if (t->type != DEVPROTO_TRANSPORT_TLS) return DEVPROTO_TLS_ERR_INVALID_CONFIG;

    tls_priv_t *priv = (tls_priv_t *)t->priv;

    if (priv->state != DEVPROTO_TLS_STATE_CONNECTED) {
        return DEVPROTO_TLS_ERR_CLOSED;
    }

    info->cipher_suite = mbedtls_ssl_get_ciphersuite(&priv->ssl_ctx);
    info->server_cn = priv->server_cn;
    info->verify_result = priv->verify_result;
    info->session_resumed = mbedtls_ssl_session_resumed(&priv->ssl_ctx);

    /* Get negotiated version */
    int version = mbedtls_ssl_get_version_number(&priv->ssl_ctx);
    info->version = (version == MBEDTLS_SSL_VERSION_TLS1_3) ?
        DEVPROTO_TLS_VERSION_1_3 : DEVPROTO_TLS_VERSION_1_2;

    return DEVPROTO_TLS_OK;
}

devproto_tls_state_t devproto_tls_get_state(devproto_transport_t *t) {
    if (!t || !t->priv) return DEVPROTO_TLS_STATE_ERROR;
    if (t->type != DEVPROTO_TRANSPORT_TLS) return DEVPROTO_TLS_STATE_ERROR;
    return ((tls_priv_t *)t->priv)->state;
}

devproto_tls_error_t devproto_tls_get_error(devproto_transport_t *t) {
    if (!t || !t->priv) return DEVPROTO_TLS_ERR_INVALID_CONFIG;
    if (t->type != DEVPROTO_TRANSPORT_TLS) return DEVPROTO_TLS_ERR_INVALID_CONFIG;
    return ((tls_priv_t *)t->priv)->last_error;
}

const char *devproto_tls_strerror(devproto_tls_error_t err) {
    switch (err) {
        case DEVPROTO_TLS_OK:                 return "Success";
        case DEVPROTO_TLS_ERR_MEMORY:         return "Memory allocation failed";
        case DEVPROTO_TLS_ERR_CERT_LOAD:      return "Failed to load certificate";
        case DEVPROTO_TLS_ERR_KEY_LOAD:       return "Failed to load private key";
        case DEVPROTO_TLS_ERR_CA_LOAD:        return "Failed to load CA certificate";
        case DEVPROTO_TLS_ERR_CONNECT:        return "TCP connection failed";
        case DEVPROTO_TLS_ERR_HANDSHAKE:      return "TLS handshake failed";
        case DEVPROTO_TLS_ERR_VERIFY:         return "Certificate verification failed";
        case DEVPROTO_TLS_ERR_SEND:           return "Send failed";
        case DEVPROTO_TLS_ERR_RECV:           return "Receive failed";
        case DEVPROTO_TLS_ERR_CLOSED:         return "Connection closed";
        case DEVPROTO_TLS_ERR_WANT_READ:      return "Operation would block (read)";
        case DEVPROTO_TLS_ERR_WANT_WRITE:     return "Operation would block (write)";
        case DEVPROTO_TLS_ERR_INVALID_CONFIG: return "Invalid configuration";
        case DEVPROTO_TLS_ERR_NOT_SUPPORTED:  return "TLS not supported";
        default:                              return "Unknown error";
    }
}

int devproto_tls_renegotiate(devproto_transport_t *t) {
    if (!t || !t->priv || !t->is_open) return DEVPROTO_TLS_ERR_CLOSED;
    if (t->type != DEVPROTO_TRANSPORT_TLS) return DEVPROTO_TLS_ERR_INVALID_CONFIG;

    tls_priv_t *priv = (tls_priv_t *)t->priv;
    int ret = mbedtls_ssl_renegotiate(&priv->ssl_ctx);

    if (ret != 0) {
        priv->last_error = DEVPROTO_TLS_ERR_HANDSHAKE;
        return ret;
    }

    return DEVPROTO_TLS_OK;
}

int devproto_tls_available(void) {
    return 1;
}

const char *devproto_tls_version(void) {
    return MBEDTLS_VERSION_STRING;
}

/* Cleanup function for transport destroy */
void devproto_transport_tls_destroy(devproto_transport_t *t) {
    if (!t || !t->priv) return;
    if (t->type != DEVPROTO_TRANSPORT_TLS) return;

    tls_priv_t *priv = (tls_priv_t *)t->priv;

    tls_close(t);

    mbedtls_ssl_free(&priv->ssl_ctx);
    mbedtls_ssl_config_free(&priv->ssl_conf);
    mbedtls_x509_crt_free(&priv->ca_cert);
    mbedtls_x509_crt_free(&priv->client_cert);
    mbedtls_pk_free(&priv->client_key);
    mbedtls_entropy_free(&priv->entropy);
    mbedtls_ctr_drbg_free(&priv->ctr_drbg);

    free(priv);
    t->priv = NULL;
}

#else /* !DEVPROTO_TLS_ENABLE */

/* Stub implementation when TLS is disabled */

#endif /* DEVPROTO_TLS_ENABLE */
