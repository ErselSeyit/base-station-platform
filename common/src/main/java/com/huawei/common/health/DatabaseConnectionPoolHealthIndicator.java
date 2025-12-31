package com.huawei.common.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.Objects;

/**
 * Custom health indicator for database connection pool metrics.
 * 
 * Shared across all services that use relational databases.
 * Provides connection pool metrics when using HikariCP.
 * 
 * Note: Only activated when a DataSource bean is available.
 * Services using NoSQL (e.g., monitoring-service with MongoDB) won't load this.
 */
@Component
@ConditionalOnBean(DataSource.class)
public class DatabaseConnectionPoolHealthIndicator implements HealthIndicator {

    private final DataSource dataSource;

    public DatabaseConnectionPoolHealthIndicator(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "DataSource cannot be null");
    }

    @Override
    public Health health() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            // Get connection pool information if available (HikariCP)
            Health.Builder builder = Health.up()
                    .withDetail("database", metaData.getDatabaseProductName())
                    .withDetail("version", metaData.getDatabaseProductVersion())
                    .withDetail("driver", metaData.getDriverName())
                    .withDetail("url", metaData.getURL());

            // Try to get HikariCP pool metrics
            addHikariPoolMetrics(builder);

            return builder.build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    private void addHikariPoolMetrics(Health.Builder builder) {
        try {
            Object hikariDataSource = dataSource;
            if (hikariDataSource.getClass().getName().contains("Hikari")) {
                java.lang.reflect.Method getHikariPoolMXBean = hikariDataSource.getClass()
                        .getMethod("getHikariPoolMXBean");
                Object poolBean = getHikariPoolMXBean.invoke(hikariDataSource);

                if (poolBean != null) {
                    java.lang.reflect.Method getActive = poolBean.getClass().getMethod("getActiveConnections");
                    java.lang.reflect.Method getIdle = poolBean.getClass().getMethod("getIdleConnections");
                    java.lang.reflect.Method getTotal = poolBean.getClass().getMethod("getTotalConnections");
                    java.lang.reflect.Method getThreadsAwaiting = poolBean.getClass().getMethod("getThreadsAwaitingConnection");

                    builder.withDetail("pool.active", getActive.invoke(poolBean))
                           .withDetail("pool.idle", getIdle.invoke(poolBean))
                           .withDetail("pool.total", getTotal.invoke(poolBean))
                           .withDetail("pool.threadsAwaiting", getThreadsAwaiting.invoke(poolBean));
                }
            }
        } catch (Exception e) {
            // HikariCP metrics not available, continue without them
            builder.withDetail("pool.metrics", "not available");
        }
    }
}
