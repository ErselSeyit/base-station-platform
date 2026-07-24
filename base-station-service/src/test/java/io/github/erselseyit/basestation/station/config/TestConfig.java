package io.github.erselseyit.basestation.station.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
public class TestConfig {

    // @TestConfiguration keeps this out of component scanning. As a plain
    // @Configuration it was picked up by IntegrationTestApplication and
    // ContractTestApplication, whose scan covers this package, and its
    // exclusions disabled JPA for those contexts.
}

