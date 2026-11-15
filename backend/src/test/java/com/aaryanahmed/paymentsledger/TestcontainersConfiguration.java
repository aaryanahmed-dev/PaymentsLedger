package com.aaryanahmed.paymentsledger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Starts a real MySQL in Docker for the tests. @ServiceConnection makes Spring Boot
 * point the datasource (and so Flyway and Hibernate) at the container automatically,
 * so the tests need no DB_URL / DB_PASSWORD and never touch a developer's local database.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        return new MySQLContainer("mysql:8.4");
    }
}
