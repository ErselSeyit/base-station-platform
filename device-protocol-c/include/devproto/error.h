/**
 * @file error.h
 * @brief Error codes and error handling utilities
 */

#ifndef DEVPROTO_ERROR_H
#define DEVPROTO_ERROR_H

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Error codes
 */
typedef enum {
    DEVPROTO_OK = 0,                /* Success */
    DEVPROTO_ERR_INVALID = -1,      /* Invalid argument */
    DEVPROTO_ERR_NOMEM = -2,        /* Out of memory */
    DEVPROTO_ERR_CRC = -3,          /* CRC check failed */
    DEVPROTO_ERR_OVERFLOW = -4,     /* Buffer overflow */
    DEVPROTO_ERR_TIMEOUT = -5,      /* Operation timed out */
    DEVPROTO_ERR_IO = -6,           /* I/O error */
    DEVPROTO_ERR_CLOSED = -7,       /* Connection closed */
    DEVPROTO_ERR_PROTOCOL = -8,     /* Protocol error */
    DEVPROTO_ERR_NOT_FOUND = -9,    /* Not found */
    DEVPROTO_ERR_BUSY = -10         /* Resource busy */
} devproto_error_t;

/**
 * Get error description string
 * @param err  Error code
 * @return     Static string describing the error
 */
const char *devproto_strerror(devproto_error_t err);

#ifdef __cplusplus
}
#endif

#endif /* DEVPROTO_ERROR_H */
