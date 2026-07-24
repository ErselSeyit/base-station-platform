package io.github.erselseyit.basestation.common.alarm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies PerceivedSeverity against 3GPP TS 28.111 clause 6, which defines
 * the alarmRecord perceivedSeverity attribute and its allowed values.
 */
class PerceivedSeverityTest {

    @Nested
    class AllowedValues {

        @Test
        void definesExactlyTheSixValuesAllowedBy28111() {
            assertThat(PerceivedSeverity.values())
                    .containsExactly(
                            PerceivedSeverity.CRITICAL,
                            PerceivedSeverity.MAJOR,
                            PerceivedSeverity.MINOR,
                            PerceivedSeverity.WARNING,
                            PerceivedSeverity.INDETERMINATE,
                            PerceivedSeverity.CLEARED);
        }
    }

    @Nested
    class Parsing {

        @Test
        void parsesCanonicalNamesCaseInsensitively() {
            assertThat(PerceivedSeverity.fromString("CRITICAL")).isEqualTo(PerceivedSeverity.CRITICAL);
            assertThat(PerceivedSeverity.fromString("major")).isEqualTo(PerceivedSeverity.MAJOR);
            assertThat(PerceivedSeverity.fromString("  Minor  ")).isEqualTo(PerceivedSeverity.MINOR);
        }

        @Test
        void mapsLegacyInfoToIndeterminate() {
            // X.733 / TS 28.111 has no INFO value. INDETERMINATE is the closest
            // non-escalating equivalent for legacy INFO-level alerts.
            assertThat(PerceivedSeverity.fromString("INFO")).isEqualTo(PerceivedSeverity.INDETERMINATE);
        }

        @Test
        void rejectsUnknownValueRatherThanCoercingIt() {
            assertThatThrownBy(() -> PerceivedSeverity.fromString("SEVERE"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SEVERE");
        }

        @Test
        void rejectsNull() {
            // perceivedSeverity is isNullable: False in TS 28.111.
            assertThatThrownBy(() -> PerceivedSeverity.fromString(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class LenientParsing {

        @Test
        void parsesTheSameValuesAsFromString() {
            assertThat(PerceivedSeverity.parse("critical")).contains(PerceivedSeverity.CRITICAL);
            assertThat(PerceivedSeverity.parse(" MAJOR ")).contains(PerceivedSeverity.MAJOR);
            assertThat(PerceivedSeverity.parse("INFO")).contains(PerceivedSeverity.INDETERMINATE);
        }

        @Test
        void returnsEmptyRatherThanThrowingForUnknownInput() {
            assertThat(PerceivedSeverity.parse("SEVERE")).isEmpty();
            assertThat(PerceivedSeverity.parse(null)).isEmpty();
            assertThat(PerceivedSeverity.parse("")).isEmpty();
        }
    }

    @Nested
    class AlarmState {

        @Test
        void onlyClearedIsInactive() {
            // TS 28.111 clause 6: an alarm is active while perceivedSeverity is not CLEARED.
            assertThat(PerceivedSeverity.CLEARED.isActive()).isFalse();
            assertThat(PerceivedSeverity.CRITICAL.isActive()).isTrue();
            assertThat(PerceivedSeverity.MAJOR.isActive()).isTrue();
            assertThat(PerceivedSeverity.MINOR.isActive()).isTrue();
            assertThat(PerceivedSeverity.WARNING.isActive()).isTrue();
            assertThat(PerceivedSeverity.INDETERMINATE.isActive()).isTrue();
        }
    }

    @Nested
    class Urgency {

        @Test
        void criticalIsMostUrgentAndClearedIsLeast() {
            assertThat(PerceivedSeverity.CRITICAL.isAtLeastAsUrgentAs(PerceivedSeverity.MAJOR)).isTrue();
            assertThat(PerceivedSeverity.MAJOR.isAtLeastAsUrgentAs(PerceivedSeverity.MINOR)).isTrue();
            assertThat(PerceivedSeverity.MINOR.isAtLeastAsUrgentAs(PerceivedSeverity.WARNING)).isTrue();
            assertThat(PerceivedSeverity.WARNING.isAtLeastAsUrgentAs(PerceivedSeverity.INDETERMINATE)).isTrue();
            assertThat(PerceivedSeverity.INDETERMINATE.isAtLeastAsUrgentAs(PerceivedSeverity.CLEARED)).isTrue();

            assertThat(PerceivedSeverity.MINOR.isAtLeastAsUrgentAs(PerceivedSeverity.CRITICAL)).isFalse();
        }

        @Test
        void severityIsReflexive() {
            assertThat(PerceivedSeverity.WARNING.isAtLeastAsUrgentAs(PerceivedSeverity.WARNING)).isTrue();
        }

        @Test
        void mostUrgentOfEmptySelectionIsCleared() {
            assertThat(PerceivedSeverity.mostUrgent()).isEqualTo(PerceivedSeverity.CLEARED);
        }

        @Test
        void mostUrgentPicksHighestUrgency() {
            assertThat(PerceivedSeverity.mostUrgent(
                    PerceivedSeverity.WARNING,
                    PerceivedSeverity.CRITICAL,
                    PerceivedSeverity.MINOR))
                    .isEqualTo(PerceivedSeverity.CRITICAL);
        }
    }
}
