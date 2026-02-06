package com.huawei.monitoring.dto;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.lang.Nullable;

/**
 * DTO for daily aggregated metrics.
 * Used by the chart endpoint to return pre-aggregated data instead of raw metrics.
 */
public class DailyMetricAggregateDTO {

    private LocalDate date;
    private Map<String, Double> averages;
    private Map<String, Long> counts;

    public DailyMetricAggregateDTO() {
    }

    public DailyMetricAggregateDTO(@Nullable LocalDate date, @Nullable Map<String, Double> averages, @Nullable Map<String, Long> counts) {
        this.date = date;
        this.averages = averages;
        this.counts = counts;
    }

    @Nullable
    public LocalDate getDate() {
        return date;
    }

    public void setDate(@Nullable LocalDate date) {
        this.date = date;
    }

    @Nullable
    public Map<String, Double> getAverages() {
        return averages;
    }

    public void setAverages(@Nullable Map<String, Double> averages) {
        this.averages = averages;
    }

    @Nullable
    public Map<String, Long> getCounts() {
        return counts;
    }

    public void setCounts(@Nullable Map<String, Long> counts) {
        this.counts = counts;
    }
}
