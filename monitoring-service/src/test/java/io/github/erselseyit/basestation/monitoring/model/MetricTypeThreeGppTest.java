package io.github.erselseyit.basestation.monitoring.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every metric records its equivalent in the 3GPP performance-measurement
 * catalogues, so the platform's metrics are traceable to the standard rather
 * than merely plausible-sounding. Names are taken from 3GPP TS 28.552
 * (5G performance measurements), with the Power/Energy/Environment family
 * carried under its "PEE" prefix.
 *
 * <p>Not every metric has a 3GPP counterpart — CPU, fans and generator fuel
 * are facility telemetry outside the RAN performance model — so the mapping
 * is an Optional, and its absence is asserted rather than left ambiguous.
 */
class MetricTypeThreeGppTest {

    @ParameterizedTest
    @EnumSource(MetricType.class)
    void everyMetricEitherMapsToAThreeGppCounterOrExplicitlyDoesNot(MetricType type) {
        // The call must not throw; a metric is either mapped or deliberately
        // unmapped, never undefined.
        Optional<String> counter = type.threeGppCounter();
        assertThat(counter).isNotNull();
        counter.ifPresent(name -> assertThat(name).isNotBlank());
    }

    @Nested
    class MappedCounters {

        @Test
        void radioThroughputMapsToTheDrbUeThroughputCounters() {
            assertThat(MetricType.DL_THROUGHPUT_NR700.threeGppCounter()).contains("DRB.UEThpDl");
            assertThat(MetricType.UL_THROUGHPUT_NR700.threeGppCounter()).contains("DRB.UEThpUl");
            assertThat(MetricType.DL_THROUGHPUT_NR3500.threeGppCounter()).contains("DRB.UEThpDl");
        }

        @Test
        void airInterfaceDelayMapsToTheAirIfDelayCounters() {
            assertThat(MetricType.LATENCY_PING.threeGppCounter()).contains("DRB.AirIfDelayDl");
            assertThat(MetricType.PACKET_DELAY.threeGppCounter()).contains("DRB.AirIfDelayDl");
        }

        @Test
        void rrcSetupMapsToTheConnectionEstablishmentCounters() {
            assertThat(MetricType.RRC_SETUP_SUCCESS.threeGppCounter()).contains("RRC.ConnEstabSucc");
            assertThat(MetricType.CONNECTION_COUNT.threeGppCounter()).contains("RRC.ConnMean");
        }

        @Test
        void mcsAndLayersMapToTheCarrierCounters() {
            assertThat(MetricType.AVG_MCS.threeGppCounter()).contains("CARR.PDSCHMCSDist");
            assertThat(MetricType.RANK_INDICATOR.threeGppCounter()).contains("CARR.AverageLayersDl");
        }

        @Test
        void handoverMapsToTheIntraSystemHandoverCounter() {
            assertThat(MetricType.HANDOVER_SUCCESS_RATE.threeGppCounter()).contains("HO.IntraSys");
        }

        @Test
        void powerAndEnvironmentMapToThePeeFamily() {
            assertThat(MetricType.POWER_CONSUMPTION.threeGppCounter()).contains("PEE.AvgPower");
            assertThat(MetricType.SITE_POWER_KWH.threeGppCounter()).contains("PEE.Energy");
            assertThat(MetricType.TEMPERATURE.threeGppCounter()).contains("PEE.AvgTemperature");
            assertThat(MetricType.UTILITY_VOLTAGE_L1.threeGppCounter()).contains("PEE.Voltage");
        }
    }

    @Nested
    class DeliberatelyUnmapped {

        @Test
        void facilityTelemetryHasNoThreeGppCounter() {
            // These are outside the RAN performance model.
            assertThat(MetricType.CPU_USAGE.threeGppCounter()).isEmpty();
            assertThat(MetricType.FAN_SPEED.threeGppCounter()).isEmpty();
            assertThat(MetricType.GENERATOR_FUEL_LEVEL.threeGppCounter()).isEmpty();
            assertThat(MetricType.SMOKE_DETECTED.threeGppCounter()).isEmpty();
        }
    }
}
