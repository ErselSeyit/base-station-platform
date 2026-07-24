package io.github.erselseyit.basestation.common.alarm;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Alarm severity as defined by ITU-T X.733 and 3GPP TS 28.111 clause 6
 * (alarmRecord {@code perceivedSeverity} attribute).
 *
 * <p>Quoting TS 28.111: <em>"It indicates the relative level of urgency for
 * operator attention."</em> The allowed values are fixed by the specification;
 * do not add to them. Declaration order is most urgent first, which
 * {@link #isAtLeastAsUrgentAs} and {@link #mostUrgent} rely on.
 *
 * <p>This is the shared vocabulary for alarms across services. It exists
 * because the platform previously carried three incompatible severity
 * models — a three-value Java enum, a five-value Python enum, and untyped
 * strings on the wire.
 */
public enum PerceivedSeverity {

    CRITICAL,
    MAJOR,
    MINOR,
    WARNING,
    INDETERMINATE,
    CLEARED;

    /** Legacy value that predates X.733 alignment; retained only for parsing. */
    private static final String LEGACY_INFO = "INFO";

    /**
     * Parses a severity name.
     *
     * <p>Accepts any canonical value case-insensitively, plus the legacy value
     * {@code INFO}, which maps to {@link #INDETERMINATE} — X.733 has no
     * informational severity, and INDETERMINATE is the closest equivalent that
     * does not escalate urgency.
     *
     * @param value severity name; must not be null ({@code perceivedSeverity}
     *              is {@code isNullable: False} in TS 28.111)
     * @return the matching severity
     * @throws IllegalArgumentException if the value is null or unrecognised
     */
    public static PerceivedSeverity fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("perceivedSeverity must not be null");
        }
        return parse(value).orElseThrow(() -> new IllegalArgumentException(
                "Unknown perceivedSeverity: '" + value + "'. Allowed values per 3GPP TS 28.111: "
                        + "CRITICAL, MAJOR, MINOR, WARNING, INDETERMINATE, CLEARED"));
    }

    /**
     * Lenient counterpart to {@link #fromString(String)}.
     *
     * <p>Use this when the input comes from a wire format and an unrecognised
     * value is an expected outcome rather than a programming error — signalling
     * that with an exception would be using exceptions for control flow
     * (<em>Effective Java</em> item 69).
     *
     * @param value severity name; may be null
     * @return the matching severity, or empty if null or unrecognised
     */
    public static Optional<PerceivedSeverity> parse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalised = value.trim().toUpperCase(Locale.ROOT);
        if (LEGACY_INFO.equals(normalised)) {
            return Optional.of(INDETERMINATE);
        }
        return Arrays.stream(values())
                .filter(severity -> severity.name().equals(normalised))
                .findFirst();
    }

    /**
     * Whether an alarm carrying this severity is still active.
     *
     * <p>Per TS 28.111 clause 6, an alarm remains in the alarm list while its
     * perceivedSeverity is not CLEARED.
     */
    public boolean isActive() {
        return this != CLEARED;
    }

    /**
     * Whether this severity demands at least as much operator attention as
     * {@code other}. Reflexive: a severity is always at least as urgent as itself.
     */
    public boolean isAtLeastAsUrgentAs(PerceivedSeverity other) {
        if (other == null) {
            throw new IllegalArgumentException("other must not be null");
        }
        return this.ordinal() <= other.ordinal();
    }

    /**
     * The most urgent severity among the given values, or {@link #CLEARED} if
     * none are supplied — an empty alarm set has nothing to escalate.
     */
    public static PerceivedSeverity mostUrgent(PerceivedSeverity... severities) {
        PerceivedSeverity highest = CLEARED;
        if (severities == null) {
            return highest;
        }
        for (PerceivedSeverity candidate : severities) {
            if (candidate != null && candidate.isAtLeastAsUrgentAs(highest)) {
                highest = candidate;
            }
        }
        return highest;
    }
}
