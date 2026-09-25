package com.educonnect.common.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxPublisherTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AtomicInteger triggers = new AtomicInteger();
    private final OutboxPublisher publisher = new OutboxPublisher(jdbcTemplate, Jackson2JsonMessageConverter::new,
            new ObjectMapper(), triggers::incrementAndGet,
            Clock.fixed(Instant.parse("2026-09-25T10:00:00Z"), ZoneOffset.UTC));

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publish_shouldStoreMessageInSameFormatAsRabbitTemplate() throws Exception {
        UUID id = publisher.publish("user-exchange", "user.registered", new SamplePayload("ayse", 3));

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(eq(OutboxPublisher.INSERT_SQL), args.capture(), args.capture(), args.capture(),
                args.capture(), args.capture(), args.capture(), args.capture(), args.capture());
        Object[] values = args.getAllValues().toArray();

        assertThat(values[0]).isEqualTo(id);
        assertThat(values[1]).isEqualTo("user-exchange");
        assertThat(values[2]).isEqualTo("user.registered");
        assertThat(new String((byte[]) values[3], StandardCharsets.UTF_8)).isEqualTo("{\"name\":\"ayse\",\"count\":3}");
        assertThat(values[4]).isEqualTo("application/json");
        assertThat(new ObjectMapper().readTree((String) values[6]).get("__TypeId__").asText())
                .isEqualTo(SamplePayload.class.getName());
    }

    @Test
    void publish_withoutTransaction_shouldTriggerRelayImmediately() {
        publisher.publish("ex", "rk", new SamplePayload("a", 1));

        assertThat(triggers).hasValue(1);
    }

    @Test
    void publish_insideTransaction_shouldTriggerRelayOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();

        publisher.publish("ex", "rk", new SamplePayload("a", 1));
        assertThat(triggers).hasValue(0);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(triggers).hasValue(1);
    }

    @Test
    void publish_toDefaultExchange_shouldStoreEmptyExchange() {
        publisher.publish(null, "some-queue", new SamplePayload("a", 1));

        ArgumentCaptor<Object> args = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).update(eq(OutboxPublisher.INSERT_SQL), args.capture(), args.capture(), args.capture(),
                args.capture(), args.capture(), args.capture(), args.capture(), args.capture());
        assertThat(args.getAllValues().get(1)).isEqualTo("");
    }

    public record SamplePayload(String name, int count) {
    }
}
