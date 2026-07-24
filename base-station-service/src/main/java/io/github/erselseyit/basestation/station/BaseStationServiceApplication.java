package io.github.erselseyit.basestation.station;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication(scanBasePackages = {"io.github.erselseyit.basestation.station", "io.github.erselseyit.basestation.common"})
@EnableJpaAuditing
public class BaseStationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaseStationServiceApplication.class, args);
    }
}

