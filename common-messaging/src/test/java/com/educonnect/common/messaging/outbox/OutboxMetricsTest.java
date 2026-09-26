package com.educonnect.common.messaging.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxMetricsTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final MeterRegistry registry = new SimpleMeterRegistry();

    @BeforeEach
    void outboxTablePresent() {
        when(jdbcTemplate.queryForObject(OutboxRelay.TABLE_EXISTS_SQL, Boolean.class)).thenReturn(true);
    }

    @Test
    void bindTo_withoutOutboxTable_shouldNotRegisterGauges() {
        when(jdbcTemplate.queryForObject(OutboxRelay.TABLE_EXISTS_SQL, Boolean.class)).thenReturn(false);

        new OutboxMetrics(jdbcTemplate).bindTo(registry);

        assertThat(registry.find("educonnect.outbox.pending").gauge()).isNull();
    }

    @Test
    void gauges_shouldReportPendingCountAndOldestAge() {
        when(jdbcTemplate.queryForObject(OutboxMetrics.PENDING_SQL, Long.class)).thenReturn(3L);
        when(jdbcTemplate.queryForObject(OutboxMetrics.OLDEST_AGE_SQL, Double.class)).thenReturn(42.5);

        new OutboxMetrics(jdbcTemplate).bindTo(registry);

        assertThat(registry.get("educonnect.outbox.pending").gauge().value()).isEqualTo(3.0);
        assertThat(registry.get("educonnect.outbox.oldest.pending.age").gauge().value()).isEqualTo(42.5);
    }

    @Test
    void gauges_withEmptyOutbox_shouldReportZero() {
        when(jdbcTemplate.queryForObject(OutboxMetrics.PENDING_SQL, Long.class)).thenReturn(0L);
        when(jdbcTemplate.queryForObject(OutboxMetrics.OLDEST_AGE_SQL, Double.class)).thenReturn(null);

        new OutboxMetrics(jdbcTemplate).bindTo(registry);

        assertThat(registry.get("educonnect.outbox.pending").gauge().value()).isZero();
        assertThat(registry.get("educonnect.outbox.oldest.pending.age").gauge().value()).isZero();
    }

    @Test
    void gauges_whenDatabaseFails_shouldReportNaN() {
        when(jdbcTemplate.queryForObject(OutboxMetrics.PENDING_SQL, Long.class))
                .thenThrow(new DataAccessResourceFailureException("down"));

        new OutboxMetrics(jdbcTemplate).bindTo(registry);

        assertThat(registry.get("educonnect.outbox.pending").gauge().value()).isNaN();
    }
}
