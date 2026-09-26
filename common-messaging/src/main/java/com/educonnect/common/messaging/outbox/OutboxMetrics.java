package com.educonnect.common.messaging.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public class OutboxMetrics implements MeterBinder {

    static final String PENDING_SQL = "SELECT count(*) FROM outbox_messages WHERE sent_at IS NULL";
    static final String OLDEST_AGE_SQL =
            "SELECT EXTRACT(EPOCH FROM (now() - min(created_at))) FROM outbox_messages WHERE sent_at IS NULL";

    private final JdbcTemplate jdbcTemplate;

    public OutboxMetrics(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        if (!outboxTableExists()) {
            return;
        }
        Gauge.builder("educonnect.outbox.pending", this, OutboxMetrics::pending)
                .description("Outbox messages waiting to be published")
                .register(registry);
        Gauge.builder("educonnect.outbox.oldest.pending.age", this, OutboxMetrics::oldestPendingAgeSeconds)
                .description("Age of the oldest unpublished outbox message")
                .baseUnit("seconds")
                .register(registry);
    }

    private boolean outboxTableExists() {
        try {
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(OutboxRelay.TABLE_EXISTS_SQL, Boolean.class));
        } catch (DataAccessException e) {
            return false;
        }
    }

    double pending() {
        try {
            Long count = jdbcTemplate.queryForObject(PENDING_SQL, Long.class);
            return count != null ? count : 0;
        } catch (DataAccessException e) {
            return Double.NaN;
        }
    }

    double oldestPendingAgeSeconds() {
        try {
            Double age = jdbcTemplate.queryForObject(OLDEST_AGE_SQL, Double.class);
            return age != null ? age : 0;
        } catch (DataAccessException e) {
            return Double.NaN;
        }
    }
}
