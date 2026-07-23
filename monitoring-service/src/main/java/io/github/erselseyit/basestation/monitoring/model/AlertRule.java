package io.github.erselseyit.basestation.monitoring.model;

import java.util.Objects;

/**
 * Immutable value object representing an alerting rule.
 *
 * <p>Built via {@link Builder}; {@code withX} methods return modified copies
 * (<em>Effective Java</em> item 2). Being a value class, it overrides
 * {@code equals}, {@code hashCode} and {@code toString} (items 10, 11 and 12) —
 * without them, two identical rules compared unequal and rules could not be
 * used reliably in sets or as map keys.
 */
public final class AlertRule {

    private final String id;
    private final String name;
    private final MetricType metricType;
    private final Operator operator;
    private final Double threshold;
    private final AlertSeverity severity;
    private final String message;
    private final boolean enabled;

    public enum Operator {
        GREATER_THAN,
        GREATER_THAN_OR_EQUAL,
        LESS_THAN,
        LESS_THAN_OR_EQUAL,
        EQUALS
    }

    private AlertRule(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.metricType = builder.metricType;
        this.operator = builder.operator;
        this.threshold = builder.threshold;
        this.severity = builder.severity;
        this.message = builder.message;
        this.enabled = builder.enabled;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * A builder pre-populated with this rule's fields.
     *
     * <p>The {@code withX} methods below all delegate here, so adding a field
     * to this class cannot leave one of them silently dropping it.
     */
    public Builder toBuilder() {
        return new Builder()
                .id(this.id)
                .name(this.name)
                .metricType(this.metricType)
                .operator(this.operator)
                .threshold(this.threshold)
                .severity(this.severity)
                .message(this.message)
                .enabled(this.enabled);
    }

    // Immutable update methods
    public AlertRule withEnabled(boolean enabled) {
        return toBuilder().enabled(enabled).build();
    }

    public AlertRule withThreshold(Double threshold) {
        return toBuilder().threshold(threshold).build();
    }

    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public MetricType getMetricType() { return metricType; }
    public Operator getOperator() { return operator; }
    public Double getThreshold() { return threshold; }
    public AlertSeverity getSeverity() { return severity; }
    public String getMessage() { return message; }
    public boolean isEnabled() { return enabled; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AlertRule)) {
            return false;
        }
        AlertRule other = (AlertRule) o;
        return enabled == other.enabled
                && Objects.equals(id, other.id)
                && Objects.equals(name, other.name)
                && metricType == other.metricType
                && operator == other.operator
                && Objects.equals(threshold, other.threshold)
                && severity == other.severity
                && Objects.equals(message, other.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, metricType, operator, threshold, severity, message, enabled);
    }

    @Override
    public String toString() {
        return "AlertRule{id=" + id
                + ", name=" + name
                + ", condition=" + metricType + " " + operator + " " + threshold
                + ", severity=" + severity
                + ", enabled=" + enabled
                + '}';
    }

    public static class Builder {
        private String id;
        private String name;
        private MetricType metricType;
        private Operator operator;
        private Double threshold;
        private AlertSeverity severity;
        private String message;
        private boolean enabled = true;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder metricType(MetricType metricType) { this.metricType = metricType; return this; }
        public Builder operator(Operator operator) { this.operator = operator; return this; }
        public Builder threshold(Double threshold) { this.threshold = threshold; return this; }
        public Builder severity(AlertSeverity severity) { this.severity = severity; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder enabled(boolean enabled) { this.enabled = enabled; return this; }

        public AlertRule build() {
            return new AlertRule(this);
        }
    }
}
