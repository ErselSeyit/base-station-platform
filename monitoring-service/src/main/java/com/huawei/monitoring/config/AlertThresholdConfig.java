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
}
