package com.educonnect.authservices.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Uygulama başlangıcında, user_roles tablosundaki CHECK constraint'in
 * ROLE_PENDING_CLUB_OFFICIAL değerini kabul edip etmediğini kontrol eder;
 * eğer etmiyorsa constraint'i güvenli şekilde günceller.
 *
 * Bu, Flyway migration'ı tamamlayıcı olarak çalışır ve farklı ortamlarda
 * config override'larından kaynaklı uygulanmayan migration senaryolarını tolere eder.
 */
@Component
public class DbConstraintFixer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DbConstraintFixer.class);

    private static final String CONSTRAINT_NAME = "user_roles_role_check";
    private static final String TABLE_NAME = "user_roles";

    private final JdbcTemplate jdbcTemplate;

    public DbConstraintFixer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        try {
            String schema = jdbcTemplate.queryForObject("select current_schema()", String.class);
            if (schema == null || schema.isBlank()) {
                schema = "public";
            }
            final String schemaName = schema; // effectively final

            String sql = "SELECT pg_get_constraintdef(c.oid) AS def FROM pg_constraint c " +
                    "JOIN pg_class t ON c.conrelid = t.oid " +
                    "JOIN pg_namespace n ON n.oid = t.relnamespace " +
                    "WHERE c.conname = ? AND t.relname = ? AND n.nspname = ?";

            String def = jdbcTemplate.query(sql, ps -> {
                ps.setString(1, CONSTRAINT_NAME);
                ps.setString(2, TABLE_NAME);
                ps.setString(3, schemaName);
            }, rs -> rs.next() ? rs.getString("def") : null);

            if (def == null) {
                log.info("Constraint {}.{} NOT FOUND in schema {}. Skipping fix.", TABLE_NAME, CONSTRAINT_NAME, schemaName);
                return;
            }

            if (def.contains("ROLE_PENDING_CLUB_OFFICIAL")) {
                log.info("Constraint {}.{} already contains ROLE_PENDING_CLUB_OFFICIAL in schema {}", TABLE_NAME, CONSTRAINT_NAME, schemaName);
                return;
            }

            log.warn("Constraint {}.{} in schema {} missing ROLE_PENDING_CLUB_OFFICIAL. Applying fix...", TABLE_NAME, CONSTRAINT_NAME, schemaName);

            String dropSql = String.format("ALTER TABLE %s.%s DROP CONSTRAINT %s", schemaName, TABLE_NAME, CONSTRAINT_NAME);
            String addSql = String.format(
                    "ALTER TABLE %s.%s ADD CONSTRAINT %s CHECK (role IN ('ROLE_STUDENT','ROLE_ACADEMICIAN','ROLE_PENDING_ACADEMICIAN','ROLE_CLUB_OFFICIAL','ROLE_PENDING_CLUB_OFFICIAL','ROLE_ADMIN'))",
                    schemaName, TABLE_NAME, CONSTRAINT_NAME
            );

            jdbcTemplate.execute(dropSql);
            jdbcTemplate.execute(addSql);

            log.info("Constraint {}.{} updated successfully in schema {}", TABLE_NAME, CONSTRAINT_NAME, schemaName);
        } catch (DataAccessException ex) {
            log.error("Failed to verify/update {}.{} constraint: {}", TABLE_NAME, CONSTRAINT_NAME, ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error while updating {}.{} constraint: {}", TABLE_NAME, CONSTRAINT_NAME, ex.getMessage());
        }
    }
}
