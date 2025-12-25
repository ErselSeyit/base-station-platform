package com.huawei.basestation.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

import com.huawei.basestation.client.MonitoringServiceClient;
import com.huawei.basestation.config.CircuitBreakerConfiguration;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
})
@ComponentScan(
        basePackages = {"com.huawei.basestation.client", "com.huawei.basestation.config"},
        useDefaultFilters = false
)
@Import({
        MonitoringServiceClient.class,
        CircuitBreakerConfiguration.class
})
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}

