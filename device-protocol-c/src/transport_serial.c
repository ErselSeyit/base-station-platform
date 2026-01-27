/**
 * @file transport_serial.c
 * @brief Serial/UART transport implementation using termios
 */

#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/select.h>

#include "devproto/transport.h"

/**
 * Serial transport private data
 */
typedef struct {
    char device[256];
    int  baudrate;
    struct termios orig_termios;
    int  termios_saved;
} serial_priv_t;

/**
 * Convert baudrate to termios speed constant
 */
static speed_t baudrate_to_speed(int baudrate)
{
    switch (baudrate) {
    case 9600:   return B9600;
    case 19200:  return B19200;
    case 38400:  return B38400;
    case 57600:  return B57600;
    case 115200: return B115200;
    case 230400: return B230400;
    case 460800: return B460800;
    case 921600: return B921600;
    default:     return B115200;
    }
}

/**
 * Open serial port
 */
static int serial_open(devproto_transport_t *t)
{
    serial_priv_t *priv = (serial_priv_t *)t->priv;
    struct termios tty;

    /* Open device */
    t->fd = open(priv->device, O_RDWR | O_NOCTTY | O_NONBLOCK);
    if (t->fd < 0) {
        return -1;
    }

    /* Save original settings */
    if (tcgetattr(t->fd, &priv->orig_termios) == 0) {
        priv->termios_saved = 1;
    }

    /* Configure serial port */
    memset(&tty, 0, sizeof(tty));

    /* Get current attributes */
    if (tcgetattr(t->fd, &tty) != 0) {
        close(t->fd);
        t->fd = -1;
        return -1;
    }

    /* Set baud rate */
    speed_t speed = baudrate_to_speed(priv->baudrate);
    cfsetispeed(&tty, speed);
    cfsetospeed(&tty, speed);

    /* 8N1 mode */
    tty.c_cflag &= ~PARENB;        /* No parity */
    tty.c_cflag &= ~CSTOPB;        /* 1 stop bit */
    tty.c_cflag &= ~CSIZE;
    tty.c_cflag |= CS8;            /* 8 data bits */

    /* No flow control */
    tty.c_cflag &= ~CRTSCTS;

    /* Enable receiver, local mode */
    tty.c_cflag |= CREAD | CLOCAL;

    /* Raw input */
    tty.c_lflag &= ~(ICANON | ECHO | ECHOE | ISIG);

    /* Raw output */
    tty.c_oflag &= ~OPOST;

    /* No input processing */
    tty.c_iflag &= ~(IXON | IXOFF | IXANY);
    tty.c_iflag &= ~(IGNBRK | BRKINT | PARMRK | ISTRIP | INLCR | IGNCR | ICRNL);

    /* Read timeout: VMIN=0, VTIME=1 = 100ms timeout */
    tty.c_cc[VMIN] = 0;
    tty.c_cc[VTIME] = 1;

    /* Apply settings */
    if (tcsetattr(t->fd, TCSANOW, &tty) != 0) {
        close(t->fd);
        t->fd = -1;
        return -1;
    }

    /* Flush buffers */
    tcflush(t->fd, TCIOFLUSH);

    t->is_open = 1;
    return 0;
}

/**
 * Close serial port
 */
static void serial_close(devproto_transport_t *t)
{
    serial_priv_t *priv = (serial_priv_t *)t->priv;

    if (t->fd >= 0) {
        /* Restore original settings */
        if (priv->termios_saved) {
            tcsetattr(t->fd, TCSANOW, &priv->orig_termios);
        }

        close(t->fd);
        t->fd = -1;
    }
    t->is_open = 0;
}

/**
 * Send data over serial
 */
static int serial_send(devproto_transport_t *t, const uint8_t *data, size_t len)
{
    if (!t->is_open || t->fd < 0) return -1;

    ssize_t written = write(t->fd, data, len);
    if (written < 0) {
        return -1;
    }

    return (int)written;
}

/**
 * Receive data from serial
 */
static int serial_recv(devproto_transport_t *t, uint8_t *data, size_t len, int timeout_ms)
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

    ssize_t n = read(t->fd, data, len);
    if (n < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return 0;
        }
        return -1;
    }

    return (int)n;
}

/**
 * Get bytes available
 */
static int serial_available(devproto_transport_t *t)
{
    if (!t->is_open || t->fd < 0) return -1;

    int bytes_available = 0;
    if (ioctl(t->fd, FIONREAD, &bytes_available) < 0) {
        return -1;
    }

    return bytes_available;
}

/**
 * Flush serial buffers
 */
static int serial_flush(devproto_transport_t *t)
{
    if (!t->is_open || t->fd < 0) return -1;

    /* Wait for output to drain */
    tcdrain(t->fd);
    return 0;
}

/* Serial transport operations vtable */
static const devproto_transport_ops_t serial_ops = {
    .open      = serial_open,
    .close     = serial_close,
    .send      = serial_send,
    .recv      = serial_recv,
    .available = serial_available,
    .flush     = serial_flush
};

/**
 * Create serial transport
 */
devproto_transport_t *devproto_transport_serial_create(const char *device, int baudrate)
{
    if (!device) return NULL;

    devproto_transport_t *t = calloc(1, sizeof(*t));
    if (!t) return NULL;

    serial_priv_t *priv = calloc(1, sizeof(*priv));
    if (!priv) {
        free(t);
        return NULL;
    }

    strncpy(priv->device, device, sizeof(priv->device) - 1);
    priv->baudrate = baudrate > 0 ? baudrate : 115200;
    priv->termios_saved = 0;

    t->type = DEVPROTO_TRANSPORT_SERIAL;
    t->ops = &serial_ops;
    t->priv = priv;
    t->fd = -1;
    t->is_open = 0;

    return t;
}
