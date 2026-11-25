package io.adumun.platform.spring.test;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostgresTestcontainerSupportTest {
    @Test
    void createsPostgresContainerWithoutStartingIt() {
        try (var postgres = PostgresTestcontainerSupport.postgres("postgres:16-alpine")) {
            assertThat(postgres.getDockerImageName()).isEqualTo("postgres:16-alpine");
            assertThat(postgres.isRunning()).isFalse();
        }
    }
}
