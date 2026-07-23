package io.github.erselseyit.basestation.monitoring.model;

/**
 * Valid units for metric values.
 *
 * <p>The mapping from metric to unit lives on {@link MetricType} itself, so it
 * cannot fall out of sync and cannot be incomplete — see <em>Effective Java</em>
 * item 34. This type only names the units and offers lookup helpers.
 */
public enum MetricUnit {
    PERCENTAGE("%"),
    CELSIUS("°C"),
    WATTS("W"),
    DBM("dBm"),
    DB("dB"),
    CONNECTIONS("connections"),
    MBPS("Mbps"),
    RPM("RPM"),
    HOURS("h"),
    MILLISECONDS("ms"),
    COUNT("count"),
    RATIO("ratio"),
    VOLTS("V"),
    AMPS("A"),
    KWH("kWh"),
    DEGREES("°"),
    KMH("km/h"),
    KILOMETRES("km"),
    MM("mm"),
    CM("cm"),
    PPM("ppm"),
    UGM3("µg/m³"),
    MHZ("MHz"),
    /** Binary sensor state: 0 or 1. Not a count of anything. */
    BOOLEAN("bool");

    private final String unit;

    MetricUnit(String unit) {
        this.unit = unit;
    }

    public String getUnit() {
        return unit;
    }

    /**
     * Gets the expected unit symbol for a metric type.
     *
     * @param metricType the metric type; must not be null
     * @return the unit symbol, never blank
     * @throws IllegalArgumentException if {@code metricType} is null
     */
    public static String getUnitForMetricType(MetricType metricType) {
        if (metricType == null) {
            throw new IllegalArgumentException("metricType must not be null");
        }
        return metricType.getUnit().getUnit();
    }

    /**
     * Validates that a unit symbol matches the one required by the metric type.
     *
     * @return false if either argument is null, or the unit does not match
     */
    public static boolean isValidUnit(MetricType metricType, String unit) {
        if (metricType == null || unit == null) {
            return false;
        }
        return metricType.getUnit().getUnit().equals(unit);
    }
}
