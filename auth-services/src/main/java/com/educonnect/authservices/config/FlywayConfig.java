package com.educonnect.authservices.config;

import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Flyway configuration to handle migration validation issues
 * This configuration is specifically for development environment
 */
@Configuration
public class FlywayConfig {

    /**
     * Development profile: Repair and migrate
     * This strategy will attempt to repair the Flyway schema history before migrating
     */
    @Bean
    @Profile("dev")
    public FlywayMigrationStrategy repairStrategy() {
        return flyway -> {
            // Attempt to repair the schema history
            flyway.repair();
            // Then migrate
            flyway.migrate();
        };
    }
}

