/**
 * @file transport_tcp.c
 * @brief TCP socket transport implementation
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <sys/socket.h>
#include <sys/select.h>
#include <sys/ioctl.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#include <netdb.h>

#include "devproto/transport.h"

/**
 * TCP transport private data
 */
typedef struct {
    char host[256];
    int  port;
} tcp_priv_t;

/**
 * Open TCP connection
 */
static int tcp_open(devproto_transport_t *t)
{
    tcp_priv_t *priv = (tcp_priv_t *)t->priv;
    struct addrinfo hints, *result, *rp;
    char port_str[16];
    int ret;

    /* Convert port to string for getaddrinfo */
    snprintf(port_str, sizeof(port_str), "%d", priv->port);

    /* Setup hints for getaddrinfo (replaces deprecated gethostbyname) */
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_INET;      /* IPv4 */
    hints.ai_socktype = SOCK_STREAM;
    hints.ai_protocol = IPPROTO_TCP;

    /* Resolve hostname */
    ret = getaddrinfo(priv->host, port_str, &hints, &result);
    if (ret != 0) {
        return -1;
    }

    /* Try each address until we connect successfully */
    for (rp = result; rp != NULL; rp = rp->ai_next) {
        t->fd = socket(rp->ai_family, rp->ai_socktype, rp->ai_protocol);
        if (t->fd < 0) {
            continue;
        }

        if (connect(t->fd, rp->ai_addr, rp->ai_addrlen) == 0) {
            break;  /* Success */
        }

        close(t->fd);
        t->fd = -1;
    }

    freeaddrinfo(result);

    if (t->fd < 0) {
        return -1;
    }

    /* Set TCP_NODELAY for low latency */
    int flag = 1;
    setsockopt(t->fd, IPPROTO_TCP, TCP_NODELAY, &flag, sizeof(flag));

    /* Set non-blocking */
    int flags = fcntl(t->fd, F_GETFL, 0);
    fcntl(t->fd, F_SETFL, flags | O_NONBLOCK);

    t->is_open = 1;
    return 0;
}

/**
 * Close TCP connection
 */
static void tcp_close(devproto_transport_t *t)
{
    if (t->fd >= 0) {
        shutdown(t->fd, SHUT_RDWR);
        close(t->fd);
        t->fd = -1;
    }
    t->is_open = 0;
}

/**
 * Send data over TCP
 */
static int tcp_send(devproto_transport_t *t, const uint8_t *data, size_t len)
{
    if (!t->is_open || t->fd < 0) return -1;

    size_t total_sent = 0;

    while (total_sent < len) {
        ssize_t sent = send(t->fd, data + total_sent, len - total_sent, MSG_NOSIGNAL);

        if (sent < 0) {
            if (errno == EAGAIN || errno == EWOULDBLOCK) {
                /* Would block, try again */
                continue;
            }
            return -1;
        } else if (sent == 0) {
            /* Connection closed */
            return -1;
        }

        total_sent += sent;
    }

    return (int)total_sent;
}

/**
 * Receive data from TCP
 */
static int tcp_recv(devproto_transport_t *t, uint8_t *data, size_t len, int timeout_ms)
{
    if (!t->is_open || t->fd < 0) return -1;

    /* Use select for timeout */
    fd_set readfds;
    struct timeval tv;

    FD_ZERO(&readfds);
    FD_SET(t->fd, &readfds);

    if (timeout_ms >= 0) {
        tv.tv_sec = timeout_ms / 1000;
        tv.tv_usec = (timeout_ms % 1000) * 1000;
    }

    int ret = select(t->fd + 1, &readfds, NULL, NULL,
                     timeout_ms >= 0 ? &tv : NULL);

    if (ret < 0) {
        return -1;  /* Error */
    } else if (ret == 0) {
        return 0;   /* Timeout */
    }

    ssize_t n = recv(t->fd, data, len, 0);

    if (n < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return 0;
        }
        return -1;
    } else if (n == 0) {
        /* Connection closed */
        t->is_open = 0;
        return -1;
    }

    return (int)n;
}

/**
 * Get bytes available
 */
static int tcp_available(devproto_transport_t *t)
{
    if (!t->is_open || t->fd < 0) return -1;

    int bytes_available = 0;
    if (ioctl(t->fd, FIONREAD, &bytes_available) < 0) {
        return -1;
    }

    return bytes_available;
}

/**
 * Flush TCP (no-op, TCP handles buffering)
 */
static int tcp_flush(devproto_transport_t *t)
{
    (void)t;
    return 0;
}

/* TCP transport operations vtable */
static const devproto_transport_ops_t tcp_ops = {
    .open      = tcp_open,
    .close     = tcp_close,
    .send      = tcp_send,
    .recv      = tcp_recv,
    .available = tcp_available,
    .flush     = tcp_flush
};

/**
 * Create TCP transport
 */
devproto_transport_t *devproto_transport_tcp_create(const char *host, int port)
{
    if (!host || port <= 0) return NULL;

    devproto_transport_t *t = calloc(1, sizeof(*t));
    if (!t) return NULL;

    tcp_priv_t *priv = calloc(1, sizeof(*priv));
    if (!priv) {
        free(t);
        return NULL;
    }

    strncpy(priv->host, host, sizeof(priv->host) - 1);
    priv->host[sizeof(priv->host) - 1] = '\0';  /* Ensure null termination */
    priv->port = port;

    t->type = DEVPROTO_TRANSPORT_TCP;
    t->ops = &tcp_ops;
    t->priv = priv;
    t->fd = -1;
    t->is_open = 0;

    return t;
}
