/**
 * @file transport.c
 * @brief Transport abstraction base implementation
 */

#include <stdlib.h>
#include "devproto/transport.h"

/**
 * Destroy transport and free resources
 */
void devproto_transport_destroy(devproto_transport_t *t)
{
    if (!t) return;

    /* Close if still open */
    if (t->is_open && t->ops && t->ops->close) {
        t->ops->close(t);
    }

    /* Free private data if allocated */
    if (t->priv) {
        free(t->priv);
        t->priv = NULL;
    }

    free(t);
}
