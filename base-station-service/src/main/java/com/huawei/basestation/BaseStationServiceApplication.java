package com.huawei.basestation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class BaseStationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BaseStationServiceApplication.class, args);
    }
}

