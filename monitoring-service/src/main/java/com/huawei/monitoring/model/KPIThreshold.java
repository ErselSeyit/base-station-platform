package com.huawei.monitoring.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * KPI Threshold configuration for 5G NR metrics.
 * Based on Huawei SSV acceptance criteria.
 */
@Document(collection = "kpi_thresholds")
public class KPIThreshold {

    @Id
    private String id;

    @Indexed(unique = true)
    private MetricType metricType;

    private String description;

    // Threshold values
    private Double minValue;       // >= minValue to pass
    private Double maxValue;       // <= maxValue to pass
    private Double warningMin;     // Warning threshold (lower bound)
    private Double warningMax;     // Warning threshold (upper bound)

    private String unit;
    private String frequencyBand;  // NR700, NR3500, or null for all
    private Integer bandwidth;     // MHz, or null for all

    // Conditions
    private String rankIndicator;  // RANK1, RANK2, RANK4 or null
    private String modulationScheme; // 256QAM, 64QAM, etc.

    private boolean enabled = true;

    // Constants for frequency bands and modulation schemes
    private static final String FREQ_NR3500 = "NR3500";
    private static final String FREQ_NR700 = "NR700";
    private static final String MOD_256QAM = "256QAM";

    // Static factory methods for common thresholds
    @SuppressWarnings("java:S100") // Method names match domain conventions
    public static KPIThreshold dlThroughputNR3500_100MHz() {
        KPIThreshold kpi = new KPIThreshold();
        kpi.setMetricType(MetricType.DL_THROUGHPUT_NR3500);
        kpi.setDescription("DL Throughput for NR3500 100MHz RANK4 256QAM");
        kpi.setMinValue(1000.0);
        kpi.setWarningMin(1100.0);
        kpi.setUnit("Mbps");
        kpi.setFrequencyBand(FREQ_NR3500);
        kpi.setBandwidth(100);
        kpi.setRankIndicator("RANK4");
        kpi.setModulationScheme(MOD_256QAM);
        return kpi;
    }

    @SuppressWarnings("java:S100")
    public static KPIThreshold dlThroughputNR3500_40MHz() {
        KPIThreshold kpi = new KPIThreshold();
        kpi.setMetricType(MetricType.DL_THROUGHPUT_NR3500);
        kpi.setDescription("DL Throughput for NR3500 40MHz RANK4 256QAM");
        kpi.setMinValue(400.0);
        kpi.setWarningMin(450.0);
        kpi.setUnit("Mbps");
        kpi.setFrequencyBand(FREQ_NR3500);
        kpi.setBandwidth(40);
        kpi.setRankIndicator("RANK4");
        kpi.setModulationScheme(MOD_256QAM);
        return kpi;
    }

    @SuppressWarnings("java:S100")
    public static KPIThreshold ulThroughputNR3500_100MHz() {
        KPIThreshold kpi = new KPIThreshold();
        kpi.setMetricType(MetricType.UL_THROUGHPUT_NR3500);
        kpi.setDescription("UL Throughput for NR3500 100MHz RANK1 256QAM");
        kpi.setMinValue(75.0);
        kpi.setWarningMin(85.0);
        kpi.setUnit("Mbps");
        kpi.setFrequencyBand(FREQ_NR3500);
        kpi.setBandwidth(100);
        kpi.setRankIndicator("RANK1");
        kpi.setModulationScheme(MOD_256QAM);
        return kpi;
    }

    public static KPIThreshold dlThroughputNR700() {
        KPIThreshold kpi = new KPIThreshold();
        kpi.setMetricType(MetricType.DL_THROUGHPUT_NR700);
        kpi.setDescription("DL Throughput for NR700 10MHz RANK2 256QAM");
        kpi.setMinValue(50.0);
        kpi.setWarningMin(60.0);
        kpi.setUnit("Mbps");
        kpi.setFrequencyBand(FREQ_NR700);
        kpi.setBandwidth(10);
        kpi.setRankIndicator("RANK2");
        kpi.setModulationScheme(MOD_256QAM);
        return kpi;
    }

    public static KPIThreshold ulThroughputNR700() {
        KPIThreshold kpi = new KPIThreshold();
        kpi.setMetricType(MetricType.UL_THROUGHPUT_NR700);
        kpi.setDescription("UL Throughput for NR700 10MHz RANK1 256QAM");
        kpi.setMinValue(20.0);
        kpi.setWarningMin(25.0);
        kpi.setUnit("Mbps");
        kpi.setFrequencyBand(FREQ_NR700);
        kpi.setBandwidth(10);
        kpi.setRankIndicator("RANK1");
        kpi.setModulationScheme(MOD_256QAM);
        return kpi;
    }

    public static KPIThreshold latency() {
        KPIThreshold kpi = new KPIThreshold();
        kpi.setMetricType(MetricType.LATENCY_PING);
        kpi.setDescription("Ping latency (32 byte, 100ms interval)");
        kpi.setMaxValue(15.0);
        kpi.setWarningMax(12.0);
        kpi.setUnit("ms");
        return kpi;
    }

    public static KPIThreshold txImbalance() {
        KPIThreshold kpi = new KPIThreshold();
        kpi.setMetricType(MetricType.TX_IMBALANCE);
        kpi.setDescription("RF Transmit Path Imbalance");
        kpi.setMaxValue(4.0);
        kpi.setWarningMax(3.0);
        kpi.setUnit("dB");
        return kpi;
    }

    public static KPIThreshold handoverSuccessRate() {
        KPIThreshold kpi = new KPIThreshold();
        kpi.setMetricType(MetricType.HANDOVER_SUCCESS_RATE);
        kpi.setDescription("Handover Success Rate");
        kpi.setMinValue(100.0);
        kpi.setWarningMin(98.0);
        kpi.setUnit("%");
        return kpi;
    }

    // Constructors
    public KPIThreshold() {
        // Default constructor for MongoDB and static factory methods
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public MetricType getMetricType() {
        return metricType;
    }

    public void setMetricType(MetricType metricType) {
        this.metricType = metricType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Double getMinValue() {
        return minValue;
    }

    public void setMinValue(Double minValue) {
        this.minValue = minValue;
    }

    public Double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Double maxValue) {
        this.maxValue = maxValue;
    }

    public Double getWarningMin() {
        return warningMin;
    }

    public void setWarningMin(Double warningMin) {
        this.warningMin = warningMin;
    }

    public Double getWarningMax() {
        return warningMax;
    }

    public void setWarningMax(Double warningMax) {
        this.warningMax = warningMax;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getFrequencyBand() {
        return frequencyBand;
    }

    public void setFrequencyBand(String frequencyBand) {
        this.frequencyBand = frequencyBand;
    }

    public Integer getBandwidth() {
        return bandwidth;
    }

    public void setBandwidth(Integer bandwidth) {
        this.bandwidth = bandwidth;
    }

    public String getRankIndicator() {
        return rankIndicator;
    }

    public void setRankIndicator(String rankIndicator) {
        this.rankIndicator = rankIndicator;
    }

    public String getModulationScheme() {
        return modulationScheme;
    }

    public void setModulationScheme(String modulationScheme) {
        this.modulationScheme = modulationScheme;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Check if a value passes this threshold.
     */
    public boolean passes(Double value) {
        if (value == null) return false;
        if (minValue != null && value < minValue) return false;
        return maxValue == null || value <= maxValue;
    }

    /**
     * Check if a value is in warning zone (passes but close to threshold).
     */
    public boolean isWarning(Double value) {
        if (value == null || !passes(value)) return false;
        if (warningMin != null && value < warningMin) return true;
        return warningMax != null && value > warningMax;
    }
}
