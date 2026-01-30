package com.huawei.basestation.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for BaseStation domain behavior methods.
 */
@DisplayName("BaseStation Domain Behavior Tests")
class BaseStationDomainTest {

    private BaseStation station;

    @BeforeEach
    void setUp() {
        station = new BaseStation("Test Station", "Test Location", 40.7128, -74.0060, StationType.MACRO_CELL);
        station.setStatus(StationStatus.ACTIVE);
    }

    @Nested
    @DisplayName("Status Transition Methods")
    class StatusTransitionTests {

        @Test
        @DisplayName("activate() should set status to ACTIVE when not in ERROR state")
        void activate_WhenNotInError_SetsStatusToActive() {
            station.setStatus(StationStatus.INACTIVE);

            station.activate();

            assertThat(station.getStatus()).isEqualTo(StationStatus.ACTIVE);
        }

        @Test
        @DisplayName("activate() should throw when station is in ERROR state")
        void activate_WhenInError_ThrowsIllegalStateException() {
            station.setStatus(StationStatus.ERROR);

            assertThatThrownBy(() -> station.activate())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot activate station in ERROR state");
        }

        @Test
        @DisplayName("deactivate() should set status to INACTIVE")
        void deactivate_SetsStatusToInactive() {
            station.deactivate();

            assertThat(station.getStatus()).isEqualTo(StationStatus.INACTIVE);
        }

        @Test
        @DisplayName("setMaintenance() should set status to MAINTENANCE")
        void setMaintenance_SetsStatusToMaintenance() {
            station.setMaintenance();

            assertThat(station.getStatus()).isEqualTo(StationStatus.MAINTENANCE);
        }

        @Test
        @DisplayName("markOffline() should set status to OFFLINE")
        void markOffline_SetsStatusToOffline() {
            station.markOffline();

            assertThat(station.getStatus()).isEqualTo(StationStatus.OFFLINE);
        }

        @Test
        @DisplayName("markError() should set status to ERROR and append description")
        void markError_SetsStatusAndAppendsDescription() {
            station.setDescription("Original description");

            station.markError("Power failure detected");

            assertThat(station.getStatus()).isEqualTo(StationStatus.ERROR);
            assertThat(station.getDescription()).contains("ERROR: Power failure detected");
            assertThat(station.getDescription()).contains("Original description");
        }

        @Test
        @DisplayName("markError() with null description should only set status")
        void markError_WithNullDescription_OnlySetsStatus() {
            station.markError(null);

            assertThat(station.getStatus()).isEqualTo(StationStatus.ERROR);
        }
    }

    @Nested
    @DisplayName("Status Query Methods")
    class StatusQueryTests {

        @Test
        @DisplayName("isOperational() should return true when ACTIVE")
        void isOperational_WhenActive_ReturnsTrue() {
            station.setStatus(StationStatus.ACTIVE);

            assertThat(station.isOperational()).isTrue();
        }

        @Test
        @DisplayName("isOperational() should return false when not ACTIVE")
        void isOperational_WhenNotActive_ReturnsFalse() {
            station.setStatus(StationStatus.MAINTENANCE);

            assertThat(station.isOperational()).isFalse();
        }

        @Test
        @DisplayName("isHealthy() should return true for ACTIVE, MAINTENANCE, INACTIVE")
        void isHealthy_ForHealthyStates_ReturnsTrue() {
            station.setStatus(StationStatus.ACTIVE);
            assertThat(station.isHealthy()).isTrue();

            station.setStatus(StationStatus.MAINTENANCE);
            assertThat(station.isHealthy()).isTrue();

            station.setStatus(StationStatus.INACTIVE);
            assertThat(station.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("isHealthy() should return false for ERROR and OFFLINE")
        void isHealthy_ForUnhealthyStates_ReturnsFalse() {
            station.setStatus(StationStatus.ERROR);
            assertThat(station.isHealthy()).isFalse();

            station.setStatus(StationStatus.OFFLINE);
            assertThat(station.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("requiresAttention() should return true for ERROR and OFFLINE")
        void requiresAttention_ForProblematicStates_ReturnsTrue() {
            station.setStatus(StationStatus.ERROR);
            assertThat(station.requiresAttention()).isTrue();

            station.setStatus(StationStatus.OFFLINE);
            assertThat(station.requiresAttention()).isTrue();
        }

        @Test
        @DisplayName("requiresAttention() should return false for healthy states")
        void requiresAttention_ForHealthyStates_ReturnsFalse() {
            station.setStatus(StationStatus.ACTIVE);
            assertThat(station.requiresAttention()).isFalse();
        }
    }

    @Nested
    @DisplayName("Distance Calculation")
    class DistanceCalculationTests {

        @Test
        @DisplayName("distanceToKm() should calculate correct distance between NYC and LA")
        void distanceToKm_NycToLa_CalculatesCorrectDistance() {
            // NYC coordinates: 40.7128° N, 74.0060° W
            station.setLatitude(40.7128);
            station.setLongitude(-74.0060);

            // LA coordinates: 34.0522° N, 118.2437° W
            double distance = station.distanceToKm(34.0522, -118.2437);

            // Expected distance: ~3935 km
            assertThat(distance).isCloseTo(3935.0, within(50.0));
        }

        @Test
        @DisplayName("distanceToKm() should return 0 for same location")
        void distanceToKm_SameLocation_ReturnsZero() {
            double distance = station.distanceToKm(station.getLatitude(), station.getLongitude());

            assertThat(distance).isCloseTo(0.0, within(0.001));
        }
    }

    @Nested
    @DisplayName("Power Consumption Check")
    class PowerConsumptionTests {

        @Test
        @DisplayName("isPowerConsumptionHigh() should return true when exceeds threshold for MACRO_CELL")
        void isPowerConsumptionHigh_MacroExceedsThreshold_ReturnsTrue() {
            station.setStationType(StationType.MACRO_CELL);
            station.setPowerConsumption(6000.0);  // Above 5000W threshold

            assertThat(station.isPowerConsumptionHigh()).isTrue();
        }

        @Test
        @DisplayName("isPowerConsumptionHigh() should return false when below threshold")
        void isPowerConsumptionHigh_BelowThreshold_ReturnsFalse() {
            station.setStationType(StationType.MACRO_CELL);
            station.setPowerConsumption(3000.0);  // Below 5000W threshold

            assertThat(station.isPowerConsumptionHigh()).isFalse();
        }

        @Test
        @DisplayName("isPowerConsumptionHigh() should return false when power is null")
        void isPowerConsumptionHigh_NullPower_ReturnsFalse() {
            station.setPowerConsumption(null);

            assertThat(station.isPowerConsumptionHigh()).isFalse();
        }

        @Test
        @DisplayName("isPowerConsumptionHigh() should use correct thresholds for each type")
        void isPowerConsumptionHigh_DifferentTypes_UsesCorrectThresholds() {
            // FEMTO_CELL threshold is 20W
            station.setStationType(StationType.FEMTO_CELL);
            station.setPowerConsumption(25.0);
            assertThat(station.isPowerConsumptionHigh()).isTrue();

            station.setPowerConsumption(15.0);
            assertThat(station.isPowerConsumptionHigh()).isFalse();
        }
    }

    @Nested
    @DisplayName("Coordinate Update")
    class CoordinateUpdateTests {

        @Test
        @DisplayName("updateCoordinates() should update valid coordinates")
        void updateCoordinates_ValidCoords_UpdatesSuccessfully() {
            station.updateCoordinates(51.5074, -0.1278);  // London

            assertThat(station.getLatitude()).isEqualTo(51.5074);
            assertThat(station.getLongitude()).isEqualTo(-0.1278);
        }

        @Test
        @DisplayName("updateCoordinates() should throw for invalid latitude")
        void updateCoordinates_InvalidLatitude_Throws() {
            assertThatThrownBy(() -> station.updateCoordinates(91.0, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Latitude");

            assertThatThrownBy(() -> station.updateCoordinates(-91.0, 0.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("updateCoordinates() should throw for invalid longitude")
        void updateCoordinates_InvalidLongitude_Throws() {
            assertThatThrownBy(() -> station.updateCoordinates(0.0, 181.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Longitude");

            assertThatThrownBy(() -> station.updateCoordinates(0.0, -181.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("updateCoordinates() should accept boundary values")
        void updateCoordinates_BoundaryValues_AcceptsSuccessfully() {
            station.updateCoordinates(90.0, 180.0);
            assertThat(station.getLatitude()).isEqualTo(90.0);
            assertThat(station.getLongitude()).isEqualTo(180.0);

            station.updateCoordinates(-90.0, -180.0);
            assertThat(station.getLatitude()).isEqualTo(-90.0);
            assertThat(station.getLongitude()).isEqualTo(-180.0);
        }
    }
}
