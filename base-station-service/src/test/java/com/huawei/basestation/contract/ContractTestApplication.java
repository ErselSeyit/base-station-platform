package com.huawei.basestation.contract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.huawei.basestation.model")
@EnableJpaRepositories("com.huawei.basestation.repository")
public class ContractTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractTestApplication.class, args);
    }
}

