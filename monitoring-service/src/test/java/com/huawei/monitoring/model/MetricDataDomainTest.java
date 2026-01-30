package com.huawei.monitoring.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for MetricData domain behavior methods.
 */
@DisplayName("MetricData Domain Behavior Tests")
class MetricDataDomainTest {

    private MetricData metric;

    @BeforeEach
    void setUp() {
        metric = new MetricData(1L, "Test Station", MetricType.CPU_USAGE, 50.0, "%");
    }

    @Nested
    @DisplayName("exceedsThreshold()")
    class ExceedsThresholdTests {

        @Test
        @DisplayName("should return true when value exceeds threshold")
        void exceedsThreshold_ValueAbove_ReturnsTrue() {
            metric.setValue(80.0);

            assertThat(metric.exceedsThreshold(75.0)).isTrue();
        }

        @Test
        @DisplayName("should return false when value is below threshold")
        void exceedsThreshold_ValueBelow_ReturnsFalse() {
            metric.setValue(50.0);

            assertThat(metric.exceedsThreshold(75.0)).isFalse();
        }

        @Test
        @DisplayName("should return false when value is null")
        void exceedsThreshold_NullValue_ReturnsFalse() {
            metric.setValue(null);

            assertThat(metric.exceedsThreshold(50.0)).isFalse();
        }
    }

    @Nested
    @DisplayName("isAnomaly()")
    class IsAnomalyTests {

        @Test
        @DisplayName("should detect high CPU usage as anomaly")
        void isAnomaly_HighCpuUsage_ReturnsTrue() {
            metric.setMetricType(MetricType.CPU_USAGE);
            metric.setValue(95.0);  // Above 90%

            assertThat(metric.isAnomaly()).isTrue();
        }

        @Test
        @DisplayName("should not flag normal CPU usage as anomaly")
        void isAnomaly_NormalCpuUsage_ReturnsFalse() {
            metric.setMetricType(MetricType.CPU_USAGE);
            metric.setValue(70.0);  // Below 90%

            assertThat(metric.isAnomaly()).isFalse();
        }

        @Test
        @DisplayName("should detect high temperature as anomaly")
        void isAnomaly_HighTemperature_ReturnsTrue() {
            metric.setMetricType(MetricType.TEMPERATURE);
            metric.setValue(90.0);  // Above 85°C

            assertThat(metric.isAnomaly()).isTrue();
        }

        @Test
        @DisplayName("should detect negative temperature as anomaly")
        void isAnomaly_NegativeTemperature_ReturnsTrue() {
            metric.setMetricType(MetricType.TEMPERATURE);
            metric.setValue(-5.0);  // Below 0°C

            assertThat(metric.isAnomaly()).isTrue();
        }

        @Test
        @DisplayName("should detect poor signal strength as anomaly")
        void isAnomaly_PoorSignalStrength_ReturnsTrue() {
            metric.setMetricType(MetricType.SIGNAL_STRENGTH);
            metric.setValue(-115.0);  // Below -110 dBm

            assertThat(metric.isAnomaly()).isTrue();
        }

        @Test
        @DisplayName("should detect high BLER as anomaly")
        void isAnomaly_HighBler_ReturnsTrue() {
            metric.setMetricType(MetricType.INITIAL_BLER);
            metric.setValue(15.0);  // Above 10%

            assertThat(metric.isAnomaly()).isTrue();
        }

        @Test
        @DisplayName("should return false when value is null")
        void isAnomaly_NullValue_ReturnsFalse() {
            metric.setValue(null);

            assertThat(metric.isAnomaly()).isFalse();
        }

        @Test
        @DisplayName("should return false when metric type is null")
        void isAnomaly_NullMetricType_ReturnsFalse() {
            metric.setMetricType(null);
            metric.setValue(100.0);

            assertThat(metric.isAnomaly()).isFalse();
        }
    }

    @Nested
    @DisplayName("isCritical()")
    class IsCriticalTests {

        @Test
        @DisplayName("should detect near-100% CPU usage as critical")
        void isCritical_ExtremeHighCpu_ReturnsTrue() {
            metric.setMetricType(MetricType.CPU_USAGE);
            metric.setValue(99.0);  // Above 98%

            assertThat(metric.isCritical()).isTrue();
        }

        @Test
        @DisplayName("should not flag high-but-not-critical CPU as critical")
        void isCritical_HighButNotCriticalCpu_ReturnsFalse() {
            metric.setMetricType(MetricType.CPU_USAGE);
            metric.setValue(95.0);  // Above 90% but below 98%

            assertThat(metric.isCritical()).isFalse();
        }

        @Test
        @DisplayName("should detect overheating temperature as critical")
        void isCritical_OverheatingTemperature_ReturnsTrue() {
            metric.setMetricType(MetricType.TEMPERATURE);
            metric.setValue(100.0);  // Above 95°C

            assertThat(metric.isCritical()).isTrue();
        }

        @Test
        @DisplayName("should detect very poor RSRP as critical")
        void isCritical_VeryPoorRsrp_ReturnsTrue() {
            metric.setMetricType(MetricType.RSRP_NR700);
            metric.setValue(-135.0);  // Below -130 dBm

            assertThat(metric.isCritical()).isTrue();
        }

        @Test
        @DisplayName("should detect high VSWR as critical")
        void isCritical_HighVswr_ReturnsTrue() {
            metric.setMetricType(MetricType.VSWR);
            metric.setValue(3.5);  // Above 3.0

            assertThat(metric.isCritical()).isTrue();
        }
    }

    @Nested
    @DisplayName("isStale()")
    class IsStaleTests {

        @Test
        @DisplayName("should return true when metric is older than threshold")
        void isStale_OldMetric_ReturnsTrue() {
            metric.setTimestamp(LocalDateTime.now().minusHours(2));

            assertThat(metric.isStale(3600)).isTrue();  // 1 hour threshold
        }

        @Test
        @DisplayName("should return false when metric is recent")
        void isStale_RecentMetric_ReturnsFalse() {
            metric.setTimestamp(LocalDateTime.now().minusMinutes(5));

            assertThat(metric.isStale(3600)).isFalse();  // 1 hour threshold
        }

        @Test
        @DisplayName("should return true when timestamp is null")
        void isStale_NullTimestamp_ReturnsTrue() {
            metric.setTimestamp(null);

            assertThat(metric.isStale(3600)).isTrue();
        }
    }

    @Nested
    @DisplayName("isThroughputMetric()")
    class IsThroughputMetricTests {

        @Test
        @DisplayName("should return true for throughput types")
        void isThroughputMetric_ThroughputTypes_ReturnsTrue() {
            metric.setMetricType(MetricType.DATA_THROUGHPUT);
            assertThat(metric.isThroughputMetric()).isTrue();

            metric.setMetricType(MetricType.DL_THROUGHPUT_NR700);
            assertThat(metric.isThroughputMetric()).isTrue();

            metric.setMetricType(MetricType.PDCP_THROUGHPUT);
            assertThat(metric.isThroughputMetric()).isTrue();
        }

        @Test
        @DisplayName("should return false for non-throughput types")
        void isThroughputMetric_NonThroughputTypes_ReturnsFalse() {
            metric.setMetricType(MetricType.CPU_USAGE);
            assertThat(metric.isThroughputMetric()).isFalse();

            metric.setMetricType(MetricType.TEMPERATURE);
            assertThat(metric.isThroughputMetric()).isFalse();
        }

        @Test
        @DisplayName("should return false when metric type is null")
        void isThroughputMetric_NullType_ReturnsFalse() {
            metric.setMetricType(null);

            assertThat(metric.isThroughputMetric()).isFalse();
        }
    }

    @Nested
    @DisplayName("isRfQualityMetric()")
    class IsRfQualityMetricTests {

        @Test
        @DisplayName("should return true for RF quality types")
        void isRfQualityMetric_RfQualityTypes_ReturnsTrue() {
            metric.setMetricType(MetricType.SIGNAL_STRENGTH);
            assertThat(metric.isRfQualityMetric()).isTrue();

            metric.setMetricType(MetricType.RSRP_NR700);
            assertThat(metric.isRfQualityMetric()).isTrue();

            metric.setMetricType(MetricType.VSWR);
            assertThat(metric.isRfQualityMetric()).isTrue();
        }

        @Test
        @DisplayName("should return false for non-RF types")
        void isRfQualityMetric_NonRfTypes_ReturnsFalse() {
            metric.setMetricType(MetricType.CPU_USAGE);
            assertThat(metric.isRfQualityMetric()).isFalse();

            metric.setMetricType(MetricType.DATA_THROUGHPUT);
            assertThat(metric.isRfQualityMetric()).isFalse();
        }
    }

    @Nested
    @DisplayName("getHealthStatus()")
    class GetHealthStatusTests {

        @Test
        @DisplayName("should return CRITICAL for critical metrics")
        void getHealthStatus_CriticalMetric_ReturnsCritical() {
            metric.setMetricType(MetricType.CPU_USAGE);
            metric.setValue(99.5);  // Critical

            assertThat(metric.getHealthStatus()).isEqualTo("CRITICAL");
        }

        @Test
        @DisplayName("should return WARNING for anomalous metrics")
        void getHealthStatus_AnomalousMetric_ReturnsWarning() {
            metric.setMetricType(MetricType.CPU_USAGE);
            metric.setValue(92.0);  // Anomaly but not critical

            assertThat(metric.getHealthStatus()).isEqualTo("WARNING");
        }

        @Test
        @DisplayName("should return NORMAL for normal metrics")
        void getHealthStatus_NormalMetric_ReturnsNormal() {
            metric.setMetricType(MetricType.CPU_USAGE);
            metric.setValue(50.0);  // Normal

            assertThat(metric.getHealthStatus()).isEqualTo("NORMAL");
        }
    }
}
