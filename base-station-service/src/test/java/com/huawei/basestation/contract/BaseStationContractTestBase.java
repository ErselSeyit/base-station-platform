package com.huawei.basestation.contract;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.huawei.basestation.dto.BaseStationDTO;
import com.huawei.basestation.model.StationStatus;
import com.huawei.basestation.model.StationType;
import com.huawei.basestation.repository.BaseStationRepository;
import com.huawei.basestation.service.BaseStationService;

import io.restassured.RestAssured;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {ContractTestApplication.class},
        properties = {
                "spring.main.allow-bean-definition-overriding=true",
                "spring.profiles.active=contract-test"
        })
public abstract class BaseStationContractTestBase {

    @LocalServerPort
    private int port;

    @Autowired
    private BaseStationRepository repository;

    @Autowired
    private BaseStationService service;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:contractdb;DB_CLOSE_DELAY=-1");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.cache.type", () -> "none");
        registry.add("spring.data.redis.host", () -> "localhost");
        registry.add("spring.jpa.properties.hibernate.globally_quoted_identifiers", () -> "true");
        registry.add("monitoring.service.url", () -> "http://localhost:8082");
    }

    @BeforeEach
    public void setUp() {
        RestAssured.baseURI = "http://localhost";
        RestAssured.port = port;

        repository.deleteAll();
        seedTestData();
    }

    private void seedTestData() {
        BaseStationDTO station1 = BaseStationDTO.builder()
                .stationName("BS-001")
                .location("Downtown NYC")
                .latitude(40.7128)
                .longitude(-74.0060)
                .stationType(StationType.MACRO_CELL)
                .status(StationStatus.ACTIVE)
                .powerConsumption(1500.0)
                .build();
        service.createStation(station1);

        BaseStationDTO station2 = BaseStationDTO.builder()
                .stationName("BS-002")
                .location("Midtown NYC")
                .latitude(40.7580)
                .longitude(-73.9855)
                .stationType(StationType.MICRO_CELL)
                .status(StationStatus.ACTIVE)
                .powerConsumption(800.0)
                .build();
        service.createStation(station2);

        BaseStationDTO station3 = BaseStationDTO.builder()
                .stationName("BS-003")
                .location("Brooklyn")
                .latitude(40.6782)
                .longitude(-73.9442)
                .stationType(StationType.SMALL_CELL)
                .status(StationStatus.MAINTENANCE)
                .powerConsumption(500.0)
                .build();
        service.createStation(station3);
    }
}
