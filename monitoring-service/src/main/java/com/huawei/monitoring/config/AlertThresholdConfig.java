package com.huawei.monitoring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Externalized configuration for alert thresholds.
 * Values can be overridden in application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "alerting.thresholds")
public class AlertThresholdConfig {

    // CPU thresholds
    private double cpuCritical = 90.0;
    private double cpuWarning = 75.0;

    // Memory thresholds
    private double memoryCritical = 95.0;
    private double memoryWarning = 85.0;

    // Temperature thresholds (Celsius)
    private double temperatureCritical = 80.0;
    private double temperatureWarning = 70.0;
    private double temperatureAnomaly = 85.0;
    private double temperatureUltraCritical = 95.0;

    // Signal strength thresholds (dBm)
    private double signalWeak = -100.0;
    private double signalAnomaly = -110.0;

    // BLER thresholds (percentage)
    private double blerCritical = 30.0;
    private double blerWarning = 15.0;

    // Battery thresholds (percentage)
    private double batteryLow = 20.0;
    private double batteryCritical = 10.0;

    // Latency thresholds (milliseconds)
    private double latencyWarning = 50.0;
    private double latencyCritical = 100.0;

    // Throughput thresholds (Mbps) - alert when below
    private double throughputLow = 50.0;
    private double throughputCritical = 20.0;

    // Power consumption thresholds (watts)
    private double powerHigh = 500.0;
    private double powerCritical = 700.0;

    // Handover success rate (percentage) - alert when below
    private double handoverWarning = 95.0;
    private double handoverCritical = 90.0;

    // Interference level (dBm) - alert when above
    private double interferenceWarning = -80.0;
    private double interferenceCritical = -70.0;

    // Getters and setters
    public double getCpuCritical() {
        return cpuCritical;
    }

    public void setCpuCritical(double cpuCritical) {
        this.cpuCritical = cpuCritical;
    }

    public double getCpuWarning() {
        return cpuWarning;
    }

    public void setCpuWarning(double cpuWarning) {
        this.cpuWarning = cpuWarning;
    }

    public double getMemoryCritical() {
        return memoryCritical;
    }

    public void setMemoryCritical(double memoryCritical) {
        this.memoryCritical = memoryCritical;
    }

    public double getMemoryWarning() {
        return memoryWarning;
    }

    public void setMemoryWarning(double memoryWarning) {
        this.memoryWarning = memoryWarning;
    }

    public double getTemperatureCritical() {
        return temperatureCritical;
    }

    public void setTemperatureCritical(double temperatureCritical) {
        this.temperatureCritical = temperatureCritical;
    }

    public double getTemperatureWarning() {
        return temperatureWarning;
    }

    public void setTemperatureWarning(double temperatureWarning) {
        this.temperatureWarning = temperatureWarning;
    }

    public double getTemperatureAnomaly() {
        return temperatureAnomaly;
    }

    public void setTemperatureAnomaly(double temperatureAnomaly) {
        this.temperatureAnomaly = temperatureAnomaly;
    }

    public double getTemperatureUltraCritical() {
        return temperatureUltraCritical;
    }

    public void setTemperatureUltraCritical(double temperatureUltraCritical) {
        this.temperatureUltraCritical = temperatureUltraCritical;
    }

    public double getSignalWeak() {
        return signalWeak;
    }

    public void setSignalWeak(double signalWeak) {
        this.signalWeak = signalWeak;
    }

    public double getSignalAnomaly() {
        return signalAnomaly;
    }

    public void setSignalAnomaly(double signalAnomaly) {
        this.signalAnomaly = signalAnomaly;
    }

    public double getBlerCritical() {
        return blerCritical;
    }

    public void setBlerCritical(double blerCritical) {
        this.blerCritical = blerCritical;
    }

    public double getBlerWarning() {
        return blerWarning;
    }

    public void setBlerWarning(double blerWarning) {
        this.blerWarning = blerWarning;
    }

    public double getBatteryLow() {
        return batteryLow;
    }

    public void setBatteryLow(double batteryLow) {
        this.batteryLow = batteryLow;
    }

    public double getBatteryCritical() {
        return batteryCritical;
    }

    public void setBatteryCritical(double batteryCritical) {
        this.batteryCritical = batteryCritical;
    }

    public double getLatencyWarning() {
        return latencyWarning;
    }

    public void setLatencyWarning(double latencyWarning) {
        this.latencyWarning = latencyWarning;
    }

    public double getLatencyCritical() {
        return latencyCritical;
    }

    public void setLatencyCritical(double latencyCritical) {
        this.latencyCritical = latencyCritical;
    }

    public double getThroughputLow() {
        return throughputLow;
    }

    public void setThroughputLow(double throughputLow) {
        this.throughputLow = throughputLow;
    }

    public double getThroughputCritical() {
        return throughputCritical;
    }

    public void setThroughputCritical(double throughputCritical) {
        this.throughputCritical = throughputCritical;
    }

    public double getPowerHigh() {
        return powerHigh;
    }

    public void setPowerHigh(double powerHigh) {
        this.powerHigh = powerHigh;
    }

    public double getPowerCritical() {
        return powerCritical;
    }

    public void setPowerCritical(double powerCritical) {
        this.powerCritical = powerCritical;
    }

    public double getHandoverWarning() {
        return handoverWarning;
    }

    public void setHandoverWarning(double handoverWarning) {
        this.handoverWarning = handoverWarning;
    }

    public double getHandoverCritical() {
        return handoverCritical;
    }

    public void setHandoverCritical(double handoverCritical) {
        this.handoverCritical = handoverCritical;
    }

    public double getInterferenceWarning() {
        return interferenceWarning;
    }

    public void setInterferenceWarning(double interferenceWarning) {
        this.interferenceWarning = interferenceWarning;
    }

    public double getInterferenceCritical() {
        return interferenceCritical;
    }

    public void setInterferenceCritical(double interferenceCritical) {
        this.interferenceCritical = interferenceCritical;
    }
}
