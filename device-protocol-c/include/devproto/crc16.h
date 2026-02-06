/**
 * @file crc16.h
 * @brief CRC-16-CCITT checksum calculation
 *
 * Polynomial: 0x1021
 * Initial value: 0xFFFF
 * Compatible with Python calculate_crc16() in device_protocol.py
 */

#ifndef DEVPROTO_CRC16_H
#define DEVPROTO_CRC16_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* CRC-16-CCITT constants */
#define DEVPROTO_CRC_INITIAL    0xFFFF
#define DEVPROTO_CRC_POLYNOMIAL 0x1021

/**
 * Calculate CRC-16-CCITT checksum (byte-by-byte)
 * @param data  Input data
 * @param len   Data length
 * @return      16-bit CRC value
 */
uint16_t devproto_crc16(const uint8_t *data, size_t len);

/**
 * Calculate CRC-16-CCITT checksum (table-driven, faster)
 * @param data  Input data
 * @param len   Data length
 * @return      16-bit CRC value
 */
uint16_t devproto_crc16_fast(const uint8_t *data, size_t len);

/**
 * Update CRC with additional data (for streaming)
 * @param crc   Current CRC value (start with DEVPROTO_CRC_INITIAL)
 * @param data  Additional data
 * @param len   Data length
 * @return      Updated CRC value
 */
uint16_t devproto_crc16_update(uint16_t crc, const uint8_t *data, size_t len);

#ifdef __cplusplus
}
#endif

#endif /* DEVPROTO_CRC16_H */
