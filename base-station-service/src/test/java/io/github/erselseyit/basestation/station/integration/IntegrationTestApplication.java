package io.github.erselseyit.basestation.station.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration.class,
        io.github.resilience4j.springboot3.retry.autoconfigure.RetryAutoConfiguration.class,
        io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration.class
})
@ComponentScan(
        basePackages = "io.github.erselseyit.basestation.station",
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = io.github.erselseyit.basestation.station.BaseStationServiceApplication.class
                ),
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = io.github.erselseyit.basestation.station.contract.ContractTestApplication.class
                )
        })
@EntityScan("io.github.erselseyit.basestation.station.model")
@EnableJpaRepositories("io.github.erselseyit.basestation.station.repository")
@EnableJpaAuditing
public class IntegrationTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntegrationTestApplication.class, args);
    }
}
