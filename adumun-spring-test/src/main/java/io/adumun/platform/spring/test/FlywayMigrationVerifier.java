package io.adumun.platform.spring.test;

import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;

public final class FlywayMigrationVerifier {
    private FlywayMigrationVerifier() {}

    public static MigrateResult migrate(Flyway flyway) {
        return Objects.requireNonNull(flyway, "flyway").migrate();
    }
}
