package com.educonnect.common.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class OutboxPublisher {

    static final String INSERT_SQL = "INSERT INTO outbox_messages "
            + "(id, exchange, routing_key, body, content_type, content_encoding, headers, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final Supplier<MessageConverter> messageConverter;
    private final ObjectMapper objectMapper;
    private final Runnable relayTrigger;
    private final Clock clock;

    public OutboxPublisher(JdbcTemplate jdbcTemplate,
                           Supplier<MessageConverter> messageConverter,
                           ObjectMapper objectMapper,
                           Runnable relayTrigger,
                           Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.messageConverter = messageConverter;
        this.objectMapper = objectMapper;
        this.relayTrigger = relayTrigger;
        this.clock = clock;
    }

    public UUID publish(String exchange, String routingKey, Object payload) {
        UUID id = UUID.randomUUID();
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(id.toString());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message message = messageConverter.get().toMessage(payload, properties);
        MessageProperties converted = message.getMessageProperties();

        jdbcTemplate.update(INSERT_SQL,
                id,
                exchange == null ? "" : exchange,
                routingKey,
                message.getBody(),
                converted.getContentType(),
                converted.getContentEncoding(),
                headersJson(converted.getHeaders()),
                Timestamp.from(clock.instant()));

        triggerRelay();
        return id;
    }

    private void triggerRelay() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    relayTrigger.run();
                }
            });
        } else {
            relayTrigger.run();
        }
    }

    private String headersJson(Map<String, Object> headers) {
        Map<String, String> values = new LinkedHashMap<>();
        headers.forEach((key, value) -> {
            if (value != null) {
                values.put(key, value.toString());
            }
        });
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox headers could not be serialized", e);
        }
    }
}
