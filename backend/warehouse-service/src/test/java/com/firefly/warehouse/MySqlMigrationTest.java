package com.firefly.warehouse;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("firefly_migration_test")
            .withUsername("firefly")
            .withPassword("firefly_test_password");

    @Test
    void migratesEmptyMySqlDatabaseThroughWarehouseVersionThree() {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .table("flyway_warehouse_schema_history")
                .locations("classpath:db/migration")
                .load();

        var result = flyway.migrate();
        assertTrue(result.success);
        assertEquals("3", flyway.info().current().getVersion().getVersion());
    }
}
