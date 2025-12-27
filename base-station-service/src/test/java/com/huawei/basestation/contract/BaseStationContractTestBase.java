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
        registry.add("eureka.client.enabled", () -> "false");
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
        BaseStationDTO station1 = new BaseStationDTO();
        station1.setStationName("BS-001");
        station1.setLocation("Downtown NYC");
        station1.setLatitude(40.7128);
        station1.setLongitude(-74.0060);
        station1.setStationType(StationType.MACRO_CELL);
        station1.setStatus(StationStatus.ACTIVE);
        station1.setPowerConsumption(1500.0);
        service.createStation(station1);

        BaseStationDTO station2 = new BaseStationDTO();
        station2.setStationName("BS-002");
        station2.setLocation("Midtown NYC");
        station2.setLatitude(40.7580);
        station2.setLongitude(-73.9855);
        station2.setStationType(StationType.MICRO_CELL);
        station2.setStatus(StationStatus.ACTIVE);
        station2.setPowerConsumption(800.0);
        service.createStation(station2);

        BaseStationDTO station3 = new BaseStationDTO();
        station3.setStationName("BS-003");
        station3.setLocation("Brooklyn");
        station3.setLatitude(40.6782);
        station3.setLongitude(-73.9442);
        station3.setStationType(StationType.SMALL_CELL);
        station3.setStatus(StationStatus.MAINTENANCE);
        station3.setPowerConsumption(500.0);
        service.createStation(station3);
    }
}
