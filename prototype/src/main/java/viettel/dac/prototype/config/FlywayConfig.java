package viettel.dac.prototype.config;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Configuration class for Flyway database migrations.
 */
@Configuration
@Slf4j
public class FlywayConfig {

    @Value("${spring.flyway.locations:classpath:db/migration}")
    private String[] locations;

    @Value("${spring.flyway.baseline-on-migrate:true}")
    private boolean baselineOnMigrate;

    @Value("${spring.flyway.table:flyway_schema_history}")
    private String table;

    @Value("${spring.flyway.validate-on-migrate:true}")
    private boolean validateOnMigrate;

    /**
     * Creates a Flyway bean for database migrations.
     *
     * @param dataSource The data source to use for migrations
     * @return A configured Flyway instance
     */
    @Bean
    @Primary
    public Flyway flyway(DataSource dataSource) {
        log.info("Initializing Flyway with locations: {}", (Object) locations);

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .baselineOnMigrate(baselineOnMigrate)
                .table(table)
                .validateOnMigrate(validateOnMigrate)
                .load();

        // Run migrations
        int pendingMigrations = flyway.info().pending().length;
        log.info("Found {} pending Flyway migrations", pendingMigrations);

        if (pendingMigrations > 0) {
            int appliedMigrations = flyway.migrate().migrationsExecuted;
            log.info("Applied {} Flyway migrations successfully", appliedMigrations);
        } else {
            log.info("No pending Flyway migrations to apply");
        }

        return flyway;
    }
}