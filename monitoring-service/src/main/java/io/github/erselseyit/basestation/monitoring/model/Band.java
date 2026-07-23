package io.github.erselseyit.basestation.monitoring.model;

import java.util.Locale;
import java.util.Optional;

/**
 * NR frequency band a metric was measured on.
 *
 * <p>Carried as a dimension of a reading rather than baked into the metric
 * type, matching the 3GPP model: a measurement (e.g. {@code DRB.UEThpDl}) is
 * reported against a measured object (an NRCellDU) that carries the frequency.
 * {@link #NONE} is used for metrics with no band — CPU, temperature, transport,
 * and so on.
 */
public enum Band {

    NONE,
    N28,   // 700 MHz
    N78;   // 3.5 GHz

    /**
     * Parses a band name case-insensitively.
     *
     * @param value the band name; may be null or blank
     * @return the matching band, or empty if null, blank or unrecognised
     */
    public static Optional<Band> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        for (Band band : values()) {
            if (band.name().equals(normalised)) {
                return Optional.of(band);
            }
        }
        return Optional.empty();
    }
}
