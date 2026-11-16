package io.adumun.platform.spring.test;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

public final class PostgresTestcontainerSupport {
    private PostgresTestcontainerSupport() {}

    public static PostgreSQLContainer<?> postgres(String image) {
        return new PostgreSQLContainer<>(image);
    }

    public static void registerDataSource(DynamicPropertyRegistry registry, PostgreSQLContainer<?> postgres) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
