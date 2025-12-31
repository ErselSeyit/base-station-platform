package com.huawei.basestation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Objects;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.basestation.dto.BaseStationDTO;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import com.huawei.basestation.repository.BaseStationRepository;

/**
 * Integration tests using Testcontainers to spin up a real PostgreSQL database.
 * 
 * These tests verify:
 * - Full API lifecycle (CRUD operations)
 * - Database constraints and validations
 * - Geospatial queries (Haversine distance calculation)
 * - Transaction boundaries
 * 
 * @see <a href="https://testcontainers.com/">Testcontainers</a>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {IntegrationTestApplication.class},
        properties = {
                "eureka.client.enabled=false",
                "spring.cache.type=none",
                "spring.main.allow-bean-definition-overriding=true",
                "spring.profiles.active=integration-test"
        })
@AutoConfigureMockMvc
@Testcontainers
@DisabledIf("skipInDemoOrNoDocker")
@DisplayName("Base Station API Integration Tests")
class BaseStationIntegrationTest {
    static boolean skipInDemoOrNoDocker() {
        try {
            boolean demoMode = Boolean.parseBoolean(System.getProperty("demo.mode",
                    String.valueOf(Boolean.parseBoolean(System.getenv().getOrDefault("DEMO_MODE", "false")))));
            boolean dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
            return demoMode || !dockerAvailable;
        } catch (Exception e) {
            return true; // be conservative: skip on any detection error
        }
    }

    @Container
    @ServiceConnection
    @SuppressWarnings("resource") // Testcontainers manages lifecycle via @Container annotation
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // Disable Eureka for tests
        registry.add("eureka.client.enabled", () -> "false");
        // Disable Redis for tests
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.cache.type", () -> "none");
        registry.add("monitoring.service.url", () -> "http://localhost:8082");
        // JPA configuration - @ServiceConnection handles DataSource, but we need to configure JPA
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @AfterAll
    static void tearDown() {
        // Container is automatically closed by Testcontainers @Container lifecycle management
        if (postgres != null && postgres.isRunning()) {
            postgres.close();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BaseStationRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Nested
    @DisplayName("CRUD Operations")
    class CrudOperations {

        @Test
        @DisplayName("Should create a new base station and return 201")
        void createStation_ReturnsCreated() throws Exception {
            BaseStationDTO station = createStationDTO("BS-001", 40.7128, -74.0060);

            MvcResult result = mockMvc.perform(post("/api/v1/stations")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(station))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.stationName").value("BS-001"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andReturn();

            // Verify it was persisted
            JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
            Long id = response.get("id").asLong();
            assertThat(repository.findById(id)).isPresent();
        }

        @Test
        @DisplayName("Should reject duplicate station names with 400")
        void createStation_WithDuplicateName_ReturnsBadRequest() throws Exception {
            // Create first station
            BaseStationDTO station1 = createStationDTO("BS-DUPLICATE", 40.0, -74.0);
            mockMvc.perform(post("/api/v1/stations")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(station1))))
                    .andExpect(status().isCreated());

            // Try to create another with same name
            BaseStationDTO station2 = createStationDTO("BS-DUPLICATE", 41.0, -75.0);
            mockMvc.perform(post("/api/v1/stations")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(station2))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should retrieve station by ID")
        void getStation_ById_ReturnsStation() throws Exception {
            // Create a station first
            BaseStationDTO station = createStationDTO("BS-GET-TEST", 40.7128, -74.0060);
            MvcResult createResult = mockMvc.perform(post("/api/v1/stations")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(station))))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
            Long id = created.get("id").asLong();

            // Retrieve it
            mockMvc.perform(get("/api/v1/stations/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.stationName").value("BS-GET-TEST"));
        }

        @Test
        @DisplayName("Should return 404 for non-existent station")
        void getStation_NotFound_Returns404() throws Exception {
            mockMvc.perform(get("/api/v1/stations/99999"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should update station and persist changes")
        void updateStation_PersistsChanges() throws Exception {
            // Create station
            BaseStationDTO station = createStationDTO("BS-UPDATE-TEST", 40.0, -74.0);
            MvcResult createResult = mockMvc.perform(post("/api/v1/stations")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(station))))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
            Long id = created.get("id").asLong();

            // Update it
            station.setStationName("BS-UPDATED");
            station.setStatus(StationStatus.MAINTENANCE);
            station.setPowerConsumption(2500.0);

            mockMvc.perform(put("/api/v1/stations/" + id)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(station))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stationName").value("BS-UPDATED"))
                    .andExpect(jsonPath("$.status").value("MAINTENANCE"))
                    .andExpect(jsonPath("$.powerConsumption").value(2500.0));

            // Verify in database
            var updated = repository.findById(id).orElseThrow();
            assertThat(updated.getStationName()).isEqualTo("BS-UPDATED");
            assertThat(updated.getStatus()).isEqualTo(StationStatus.MAINTENANCE);
        }

        @Test
        @DisplayName("Should delete station and return 204")
        void deleteStation_ReturnsNoContent() throws Exception {
            // Create station
            BaseStationDTO station = createStationDTO("BS-DELETE-TEST", 40.0, -74.0);
            MvcResult createResult = mockMvc.perform(post("/api/v1/stations")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(station))))
                    .andExpect(status().isCreated())
                    .andReturn();

            JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());
            Long id = created.get("id").asLong();

            // Delete it
            mockMvc.perform(delete("/api/v1/stations/" + id))
                    .andExpect(status().isNoContent());

            // Verify it's gone
            assertThat(repository.findById(id)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("Should reject station without required fields")
        void createStation_WithoutRequiredFields_ReturnsBadRequest() throws Exception {
            BaseStationDTO invalidStation = new BaseStationDTO();
            // Missing all required fields

            mockMvc.perform(post("/api/v1/stations")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(invalidStation))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should reject negative power consumption")
        void createStation_WithNegativePower_ReturnsBadRequest() throws Exception {
            BaseStationDTO station = createStationDTO("BS-NEGATIVE", 40.0, -74.0);
            station.setPowerConsumption(-100.0);

            mockMvc.perform(post("/api/v1/stations")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(station))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Geospatial Queries")
    class GeospatialQueries {

        @Test
        @DisplayName("Should find stations within bounding box")
        void findStationsInArea_ReturnsMatchingStations() throws Exception {
            // Create stations at different locations
            createAndSaveStation("BS-NYC", 40.7128, -74.0060); // NYC
            createAndSaveStation("BS-BOSTON", 42.3601, -71.0589); // Boston
            createAndSaveStation("BS-LA", 34.0522, -118.2437); // LA (outside area)

            // Search for stations in Northeast US
            mockMvc.perform(get("/api/v1/stations/search/area")
                    .param("minLat", "40.0")
                    .param("maxLat", "43.0")
                    .param("minLon", "-75.0")
                    .param("maxLon", "-70.0"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[?(@.stationName == 'BS-NYC')]").exists())
                    .andExpect(jsonPath("$[?(@.stationName == 'BS-BOSTON')]").exists())
                    .andExpect(jsonPath("$[?(@.stationName == 'BS-LA')]").doesNotExist());
        }

        @Test
        @DisplayName("Should find stations within radius using Haversine formula")
        void findStationsNearby_UsesHaversineDistance() throws Exception {
            // Times Square, NYC
            double centerLat = 40.7580;
            double centerLon = -73.9855;

            // Create stations at known distances from Times Square
            createAndSaveStation("BS-TIMES-SQ", 40.7580, -73.9855); // 0 km (at center)
            createAndSaveStation("BS-CENTRAL-PARK", 40.7829, -73.9654); // ~3 km north
            createAndSaveStation("BS-BROOKLYN", 40.6782, -73.9442); // ~10 km south
            createAndSaveStation("BS-NEWARK", 40.7357, -74.1724); // ~17 km west

            // Search within 5km radius
            mockMvc.perform(get("/api/v1/stations/search/nearby")
                    .param("lat", String.valueOf(centerLat))
                    .param("lon", String.valueOf(centerLon))
                    .param("radiusKm", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].stationName").value("BS-TIMES-SQ")) // Closest first
                    .andExpect(jsonPath("$[1].stationName").value("BS-CENTRAL-PARK"));
        }

        @Test
        @DisplayName("Should return empty list when no stations in radius")
        void findStationsNearby_NoResults_ReturnsEmptyList() throws Exception {
            createAndSaveStation("BS-TOKYO", 35.6762, 139.6503); // Tokyo

            // Search near NYC (no stations there)
            mockMvc.perform(get("/api/v1/stations/search/nearby")
                    .param("lat", "40.7128")
                    .param("lon", "-74.0060")
                    .param("radiusKm", "100"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    @DisplayName("Filtering")
    class Filtering {

        @Test
        @DisplayName("Should filter stations by status")
        void getAllStations_FilterByStatus() throws Exception {
            createAndSaveStation("BS-ACTIVE-1", 40.0, -74.0, StationStatus.ACTIVE);
            createAndSaveStation("BS-ACTIVE-2", 41.0, -74.0, StationStatus.ACTIVE);
            createAndSaveStation("BS-MAINTENANCE", 42.0, -74.0, StationStatus.MAINTENANCE);

            mockMvc.perform(get("/api/v1/stations")
                    .param("status", "ACTIVE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));

            mockMvc.perform(get("/api/v1/stations")
                    .param("status", "MAINTENANCE"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("Should filter stations by type")
        void getAllStations_FilterByType() throws Exception {
            createAndSaveStation("BS-MACRO", 40.0, -74.0, StationType.MACRO_CELL);
            createAndSaveStation("BS-MICRO", 41.0, -74.0, StationType.MICRO_CELL);

            mockMvc.perform(get("/api/v1/stations")
                    .param("type", "MACRO_CELL"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].stationName").value("BS-MACRO"));
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    private BaseStationDTO createStationDTO(String name, double lat, double lon) {
        BaseStationDTO dto = new BaseStationDTO();
        dto.setStationName(name);
        dto.setLocation("Test Location");
        dto.setLatitude(lat);
        dto.setLongitude(lon);
        dto.setStationType(StationType.MACRO_CELL);
        dto.setStatus(StationStatus.ACTIVE);
        dto.setPowerConsumption(1500.0);
        return dto;
    }

    private void createAndSaveStation(String name, double lat, double lon) throws Exception {
        createAndSaveStation(name, lat, lon, StationStatus.ACTIVE);
    }

    private void createAndSaveStation(String name, double lat, double lon, StationStatus status) throws Exception {
        BaseStationDTO dto = createStationDTO(name, lat, lon);
        dto.setStatus(status);
        mockMvc.perform(post("/api/v1/stations")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(dto))))
                .andExpect(status().isCreated());
    }

    private void createAndSaveStation(String name, double lat, double lon, StationType type) throws Exception {
        BaseStationDTO dto = createStationDTO(name, lat, lon);
        dto.setStationType(type);
        mockMvc.perform(post("/api/v1/stations")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(dto))))
                .andExpect(status().isCreated());
    }
}
