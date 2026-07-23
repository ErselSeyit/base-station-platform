package io.github.erselseyit.basestation.monitoring.model;

import java.util.Optional;

/**
 * Metric types for base station monitoring.
 *
 * <p>Each constant carries its own unit, so adding a metric without deciding
 * its unit is a compile error rather than a runtime failure. See
 * <em>Effective Java</em> item 34 — associate data with enum constants instead
 * of maintaining a parallel lookup table.
 *
 * <p>RAN and power/environment metrics additionally record their equivalent in
 * the 3GPP performance-measurement catalogues, so a metric is traceable to the
 * standard rather than merely plausible-sounding. Counter names are from
 * 3GPP TS 28.552 (5G performance measurements); the Power, Energy and
 * Environment family is carried under its "PEE" prefix. Facility telemetry
 * (CPU, fans, generator fuel, safety sensors) sits outside the RAN performance
 * model and is deliberately left unmapped.
 *
 * <p>The counter name is a documented reference, not a wire or storage
 * identifier: the platform still uses these enum names on the wire. Aligning
 * the on-the-wire identity with the standard — dropping the band from the name
 * and carrying it as a measured-object dimension per NRCellDU — is a separate
 * migration that touches the device firmware and stored data.
 */
public enum MetricType {

    // Infrastructure Metrics
    CPU_USAGE(MetricUnit.PERCENTAGE),
    MEMORY_USAGE(MetricUnit.PERCENTAGE),
    TEMPERATURE(MetricUnit.CELSIUS, "PEE.AvgTemperature"),
    POWER_CONSUMPTION(MetricUnit.WATTS, "PEE.AvgPower"),
    FAN_SPEED(MetricUnit.RPM),
    UPTIME(MetricUnit.HOURS),
    CONNECTION_COUNT(MetricUnit.CONNECTIONS, "RRC.ConnMean"),

    // Legacy RF Metrics
    SIGNAL_STRENGTH(MetricUnit.DBM),
    DATA_THROUGHPUT(MetricUnit.MBPS),

    // 5G NR700 (n28) Metrics - 700MHz Band
    DL_THROUGHPUT_NR700(MetricUnit.MBPS, "DRB.UEThpDl"),
    UL_THROUGHPUT_NR700(MetricUnit.MBPS, "DRB.UEThpUl"),
    RSRP_NR700(MetricUnit.DBM),
    SINR_NR700(MetricUnit.DB),

    // 5G NR3500 (n78) Metrics - 3.5GHz Band
    DL_THROUGHPUT_NR3500(MetricUnit.MBPS, "DRB.UEThpDl"),
    UL_THROUGHPUT_NR3500(MetricUnit.MBPS, "DRB.UEThpUl"),
    RSRP_NR3500(MetricUnit.DBM),
    SINR_NR3500(MetricUnit.DB),

    // 5G Radio Metrics
    PDCP_THROUGHPUT(MetricUnit.MBPS, "DRB.PdcpSduVolumeDL"),
    RLC_THROUGHPUT(MetricUnit.MBPS),
    INITIAL_BLER(MetricUnit.PERCENTAGE),
    AVG_MCS(MetricUnit.COUNT, "CARR.PDSCHMCSDist"),
    RB_PER_SLOT(MetricUnit.COUNT),
    RANK_INDICATOR(MetricUnit.COUNT, "CARR.AverageLayersDl"),

    // RF Quality Metrics
    TX_IMBALANCE(MetricUnit.DB),
    LATENCY_PING(MetricUnit.MILLISECONDS, "DRB.AirIfDelayDl"),
    HANDOVER_SUCCESS_RATE(MetricUnit.PERCENTAGE, "HO.IntraSys"),
    INTERFERENCE_LEVEL(MetricUnit.DBM),
    VSWR(MetricUnit.RATIO),

    // Carrier Aggregation
    CA_DL_THROUGHPUT(MetricUnit.MBPS, "DRB.UEThpDl"),
    CA_UL_THROUGHPUT(MetricUnit.MBPS, "DRB.UEThpUl"),

    // ========================================================================
    // Extended Metrics (Phase 2 Enhancement)
    // ========================================================================

    // Power & Energy Metrics (0x80-0x8F)
    UTILITY_VOLTAGE_L1(MetricUnit.VOLTS, "PEE.Voltage"),
    UTILITY_VOLTAGE_L2(MetricUnit.VOLTS, "PEE.Voltage"),
    UTILITY_VOLTAGE_L3(MetricUnit.VOLTS, "PEE.Voltage"),
    POWER_FACTOR(MetricUnit.RATIO),
    GENERATOR_FUEL_LEVEL(MetricUnit.PERCENTAGE),
    GENERATOR_RUNTIME(MetricUnit.HOURS),
    BATTERY_SOC(MetricUnit.PERCENTAGE),
    BATTERY_DOD(MetricUnit.PERCENTAGE),
    BATTERY_CELL_TEMP_MIN(MetricUnit.CELSIUS, "PEE.MinTemperature"),
    BATTERY_CELL_TEMP_MAX(MetricUnit.CELSIUS, "PEE.MaxTemperature"),
    SOLAR_PANEL_VOLTAGE(MetricUnit.VOLTS),
    SOLAR_CHARGE_CURRENT(MetricUnit.AMPS),
    SITE_POWER_KWH(MetricUnit.KWH, "PEE.Energy"),

    // Environmental & Safety Metrics (0x90-0x9F)
    WIND_SPEED(MetricUnit.KMH),
    WIND_DIRECTION(MetricUnit.DEGREES),
    PRECIPITATION(MetricUnit.MM),
    LIGHTNING_DISTANCE(MetricUnit.KILOMETRES),
    TILT_ANGLE(MetricUnit.DEGREES),
    VIBRATION_LEVEL(MetricUnit.COUNT),
    WATER_LEVEL(MetricUnit.CM),
    PM25_LEVEL(MetricUnit.UGM3),
    SMOKE_DETECTED(MetricUnit.BOOLEAN),
    CO_LEVEL(MetricUnit.PPM),
    DOOR_STATUS(MetricUnit.BOOLEAN),
    MOTION_DETECTED(MetricUnit.BOOLEAN),

    // Transport/Backhaul Metrics (0xA0-0xAF)
    FIBER_RX_POWER(MetricUnit.DBM),
    FIBER_TX_POWER(MetricUnit.DBM),
    FIBER_BER(MetricUnit.RATIO),
    FIBER_OSNR(MetricUnit.DB),
    MW_RSL(MetricUnit.DBM),
    MW_SNR(MetricUnit.DB),
    MW_MODULATION(MetricUnit.COUNT),
    ETH_UTILIZATION(MetricUnit.PERCENTAGE),
    ETH_ERRORS(MetricUnit.COUNT),
    ETH_LATENCY(MetricUnit.MILLISECONDS),
    PTP_OFFSET(MetricUnit.MILLISECONDS),
    GPS_SATELLITES(MetricUnit.COUNT),

    // Advanced Radio Metrics (0xB0-0xBF)
    BEAM_WEIGHT_MAG(MetricUnit.RATIO),
    BEAM_WEIGHT_PHASE(MetricUnit.DEGREES),
    PRECODING_RANK(MetricUnit.COUNT),
    PIM_LEVEL(MetricUnit.DBM),
    CO_CHANNEL_INTERFERENCE(MetricUnit.DBM),
    OCCUPIED_BANDWIDTH(MetricUnit.MHZ),
    ACLR(MetricUnit.DB),
    GTP_THROUGHPUT(MetricUnit.MBPS),
    PACKET_DELAY(MetricUnit.MILLISECONDS, "DRB.AirIfDelayDl"),
    RRC_SETUP_SUCCESS(MetricUnit.PERCENTAGE, "RRC.ConnEstabSucc"),
    PAGING_SUCCESS(MetricUnit.PERCENTAGE),

    // Network Slicing Metrics (0xC0-0xCF) - 5G specific
    SLICE_THROUGHPUT(MetricUnit.MBPS),
    SLICE_LATENCY(MetricUnit.MILLISECONDS),
    SLICE_PACKET_LOSS(MetricUnit.PERCENTAGE),
    SLICE_PRB_UTIL(MetricUnit.PERCENTAGE, "RRU.PrbUsedDl"),
    SLICE_SLA_COMPLIANCE(MetricUnit.PERCENTAGE);

    private final MetricUnit unit;
    private final String threeGppCounter;

    MetricType(MetricUnit unit) {
        this(unit, null);
    }

    MetricType(MetricUnit unit, String threeGppCounter) {
        this.unit = unit;
        this.threeGppCounter = threeGppCounter;
    }

    /** The unit this metric is always measured in. Never null. */
    public MetricUnit getUnit() {
        return unit;
    }

    /**
     * The 3GPP TS 28.552 counter name this metric corresponds to, or empty for
     * facility telemetry that sits outside the RAN performance model.
     */
    public Optional<String> threeGppCounter() {
        return Optional.ofNullable(threeGppCounter);
    }
}
