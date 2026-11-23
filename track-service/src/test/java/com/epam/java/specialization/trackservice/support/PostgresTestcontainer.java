package com.epam.java.specialization.trackservice.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A real PostgreSQL in Docker (the same image as k8s/databases/catalog-db.yaml) instead of H2.
 * {@code @ServiceConnection} points spring.datasource.* at the container, so nothing has to be started by hand.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestcontainer {

    public static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:17-alpine");

    @Bean
    @ServiceConnection
    PostgreSQLContainer catalogDb() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
