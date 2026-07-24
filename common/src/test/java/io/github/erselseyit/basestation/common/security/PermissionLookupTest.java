package io.github.erselseyit.basestation.common.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lookup methods return Optional rather than null.
 *
 * <em>Effective Java</em> item 55: a method that may legitimately find nothing
 * should say so in its type. These feed authorization decisions, where a null
 * that slips through an unchecked dereference is a security-relevant bug.
 */
class PermissionLookupTest {

    @Nested
    class ByResourceAndAction {

        @Test
        void findsAMatchIgnoringCase() {
            assertThat(Permission.findByResourceAndAction("STATION", "UPDATE"))
                    .contains(Permission.STATION_UPDATE);
            assertThat(Permission.findByResourceAndAction("station", "update"))
                    .contains(Permission.STATION_UPDATE);
        }

        @Test
        void isEmptyWhenNothingMatches() {
            assertThat(Permission.findByResourceAndAction("station", "teleport")).isEmpty();
            assertThat(Permission.findByResourceAndAction("nonsense", "update")).isEmpty();
        }

        @Test
        void isEmptyForNullArguments() {
            assertThat(Permission.findByResourceAndAction(null, "update")).isEmpty();
            assertThat(Permission.findByResourceAndAction("station", null)).isEmpty();
        }
    }

    @Nested
    class ByKey {

        @Test
        void findsAMatchForAWellFormedKey() {
            assertThat(Permission.findByKey("station:update")).contains(Permission.STATION_UPDATE);
        }

        @Test
        void isEmptyForMalformedKeys() {
            assertThat(Permission.findByKey(null)).isEmpty();
            assertThat(Permission.findByKey("")).isEmpty();
            assertThat(Permission.findByKey("station")).isEmpty();
        }

        @Test
        void splitsOnlyOnTheFirstSeparator() {
            // A trailing segment must not silently match a different permission.
            assertThat(Permission.findByKey("station:update:extra")).isEmpty();
        }
    }

    @Nested
    class ByName {

        @Test
        void findsTheEnumConstantIgnoringCase() {
            assertThat(Permission.fromName("STATION_UPDATE")).contains(Permission.STATION_UPDATE);
            assertThat(Permission.fromName("station_update")).contains(Permission.STATION_UPDATE);
        }

        @Test
        void isEmptyForUnknownNamesWithoutThrowing() {
            // Item 69: an unknown name is an expected outcome here, not an
            // exceptional condition, so it must not be signalled by an exception.
            assertThat(Permission.fromName("NOT_A_PERMISSION")).isEmpty();
            assertThat(Permission.fromName(null)).isEmpty();
        }
    }
}
