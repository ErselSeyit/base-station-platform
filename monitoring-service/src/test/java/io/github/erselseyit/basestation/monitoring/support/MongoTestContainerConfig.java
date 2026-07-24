package io.github.erselseyit.basestation.monitoring.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Provides a throwaway MongoDB for tests that need a real one.
 *
 * <p>Import this instead of relying on a MongoDB being present on the host.
 * {@code application-test.yml} previously pointed at
 * {@code mongodb://localhost:27017}, so these tests passed only when an
 * unauthenticated MongoDB happened to be running locally, and failed against
 * the project's own docker-compose stack, which enables authentication.
 *
 * <p>{@code @ServiceConnection} lets Spring Boot derive the connection
 * properties from the container, so no URI needs to be hardcoded anywhere.
 * Spring Boot manages the container lifecycle for container beans.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MongoTestContainerConfig {

    /** Matches the mongo image used by docker-compose. */
    private static final DockerImageName MONGO_IMAGE = DockerImageName.parse("mongo:8");

    @Bean
    @ServiceConnection
    @SuppressWarnings("resource") // lifecycle is managed by Spring Boot
    MongoDBContainer mongoDbContainer() {
        return new MongoDBContainer(MONGO_IMAGE);
    }
}
