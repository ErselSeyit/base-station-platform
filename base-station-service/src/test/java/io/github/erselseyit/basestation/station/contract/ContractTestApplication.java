package io.github.erselseyit.basestation.station.contract;

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
        basePackages = {"io.github.erselseyit.basestation.station",
                        // BaseStationServiceApplication scans common too; the test
                        // applications must match or beans like AuditLogger go missing.
                        "io.github.erselseyit.basestation.common"},
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.CUSTOM,
                        classes = org.springframework.boot.context.TypeExcludeFilter.class
                ),
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = io.github.erselseyit.basestation.station.test.TestApplication.class
                ),
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = io.github.erselseyit.basestation.station.BaseStationServiceApplication.class
                ),
                @ComponentScan.Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = io.github.erselseyit.basestation.station.integration.IntegrationTestApplication.class
                )
        })
@EntityScan("io.github.erselseyit.basestation.station.model")
@EnableJpaRepositories("io.github.erselseyit.basestation.station.repository")
@EnableJpaAuditing
public class ContractTestApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContractTestApplication.class, args);
    }
}
