package com.huawei.monitoring.event;

import org.springframework.context.ApplicationEvent;

import com.huawei.monitoring.dto.MetricDataDTO;

/**
 * Event published when a new metric is successfully recorded.
 * Allows loosely-coupled observers to react to metric recording without coupling
 * the MonitoringService to WebSocket broadcasting or alerting logic.
 */
public class MetricRecordedEvent extends ApplicationEvent {

    private final transient MetricDataDTO metric;

    public MetricRecordedEvent(Object source, MetricDataDTO metric) {
        super(source);
        this.metric = metric;
    }

    public MetricDataDTO getMetric() {
        return metric;
    }
}