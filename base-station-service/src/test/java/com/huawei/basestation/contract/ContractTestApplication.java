package com.huawei.basestation.contract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(
        exclude = {
                RedisAutoConfiguration.class,
                RedisRepositoriesAutoConfiguration.class
        })
@ComponentScan(
        basePackages = "com.huawei.basestation",
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = com.huawei.basestation.BaseStationServiceApplication.class
                ),
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = com.huawei.basestation.integration.IntegrationTestApplication.class
                )
        })
@EntityScan("com.huawei.basestation.model")
@EnableJpaRepositories("com.huawei.basestation.repository")
@EnableJpaAuditing
public class ContractTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractTestApplication.class, args);
    }
}
