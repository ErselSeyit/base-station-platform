package com.huawei.basestation.model;

/**
 * Protocol used for managing/communicating with a base station.
 * Determines how the platform sends commands and receives metrics.
 */
public enum ManagementProtocol {
    /**
     * Direct API communication - station has its own REST/gRPC API.
     */
    DIRECT,

    /**
     * Simple Network Management Protocol - via edge-bridge.
     */
    SNMP,

    /**
     * NETCONF protocol - via edge-bridge.
     */
    NETCONF,

    /**
     * Modbus protocol - via edge-bridge (industrial equipment).
     */
    MODBUS,

    /**
     * O-RAN protocol - Open RAN interface standard.
     */
    ORAN
}
