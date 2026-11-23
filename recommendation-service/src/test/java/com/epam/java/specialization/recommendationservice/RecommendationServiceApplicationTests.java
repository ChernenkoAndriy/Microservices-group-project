package com.epam.java.specialization.recommendationservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.clickhouse.ClickHouseContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application starts against a real ClickHouse (same image as k8s/databases/recommendation-db.yaml),
 * started by Testcontainers, and creates its analytics table on startup.
 */
@SpringBootTest
@Testcontainers
class RecommendationServiceApplicationTests {

    @Container
    static final ClickHouseContainer CLICKHOUSE = new ClickHouseContainer("clickhouse/clickhouse-server:26.10.3.7");

    @DynamicPropertySource
    static void clickHouse(DynamicPropertyRegistry registry) {
        registry.add("CLICKHOUSE_URL", CLICKHOUSE::getJdbcUrl);
        registry.add("CLICKHOUSE_USER", CLICKHOUSE::getUsername);
        registry.add("CLICKHOUSE_PASSWORD", CLICKHOUSE::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndCreatesTheAnalyticsTable() {
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT count() FROM system.tables WHERE name = 'user_listen_events'", Integer.class);
        assertThat(tables).isEqualTo(1);
    }
}
