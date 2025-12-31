package com.huawei.basestation.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        EurekaClientAutoConfiguration.class,
        io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration.class,
        io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration.class,
        io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration.class
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
                        classes = com.huawei.basestation.contract.ContractTestApplication.class
                )
        })
@EntityScan("com.huawei.basestation.model")
@EnableJpaRepositories("com.huawei.basestation.repository")
@EnableJpaAuditing
public class IntegrationTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntegrationTestApplication.class, args);
    }
}

