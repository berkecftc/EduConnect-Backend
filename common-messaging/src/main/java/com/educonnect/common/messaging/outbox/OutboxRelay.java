package com.educonnect.common.messaging.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class OutboxRelay implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int MAX_ERROR_LENGTH = 500;
    private static final long CLEANUP_INTERVAL_MILLIS = TimeUnit.HOURS.toMillis(1);
    private static final TypeReference<Map<String, String>> HEADERS_TYPE = new TypeReference<>() {
    };

    static final String TABLE_EXISTS_SQL = "SELECT to_regclass('outbox_messages') IS NOT NULL";
    static final String SELECT_PENDING_SQL = "SELECT id, exchange, routing_key, body, content_type, content_encoding, headers "
            + "FROM outbox_messages WHERE sent_at IS NULL ORDER BY created_at, id LIMIT ? FOR UPDATE SKIP LOCKED";
    static final String MARK_SENT_SQL = "UPDATE outbox_messages SET sent_at = ?, attempts = attempts + 1, last_error = NULL WHERE id = ?";
    static final String MARK_FAILED_SQL = "UPDATE outbox_messages SET attempts = attempts + 1, last_error = ? WHERE id = ?";
    static final String DELETE_SENT_SQL = "DELETE FROM outbox_messages WHERE sent_at IS NOT NULL AND sent_at < ?";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Supplier<RabbitTemplate> rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxProperties properties;
    private final Clock clock;
    private final AtomicBoolean pending = new AtomicBoolean();

    private volatile ScheduledExecutorService executor;
    private volatile boolean running;
    private volatile boolean active;
    private volatile long lastCleanup;

    public OutboxRelay(JdbcTemplate jdbcTemplate,
                       TransactionTemplate transactionTemplate,
                       Supplier<RabbitTemplate> rabbitTemplate,
                       ObjectMapper objectMapper,
                       OutboxProperties properties,
                       Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void start() {
        running = true;
        active = tableExists();
        if (!active) {
            log.info("Outbox relay inactive: table outbox_messages not found");
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "outbox-relay");
            thread.setDaemon(true);
            return thread;
        });
        long interval = Math.max(100, properties.pollInterval().toMillis());
        executor.scheduleWithFixedDelay(this::runSafely, interval, interval, TimeUnit.MILLISECONDS);
        log.info("Outbox relay started (poll every {} ms, batch {})", interval, properties.batchSize());
    }

    public void trigger() {
        ScheduledExecutorService current = executor;
        if (active && current != null && pending.compareAndSet(false, true)) {
            current.execute(() -> {
                pending.set(false);
                runSafely();
            });
        }
    }

    private void runSafely() {
        try {
            relayPending();
            cleanupIfDue();
        } catch (RuntimeException e) {
            log.warn("Outbox relay run failed: {}", e.getMessage());
        }
    }

    public int relayPending() {
        Integer sent = transactionTemplate.execute(status -> {
            List<OutboxRow> rows = jdbcTemplate.query(SELECT_PENDING_SQL, (rs, rowNum) -> new OutboxRow(
                    rs.getObject("id", UUID.class),
                    rs.getString("exchange"),
                    rs.getString("routing_key"),
                    rs.getBytes("body"),
                    rs.getString("content_type"),
                    rs.getString("content_encoding"),
                    rs.getString("headers")), Math.max(1, properties.batchSize()));
            int count = 0;
            for (OutboxRow row : rows) {
                try {
                    send(row);
                    jdbcTemplate.update(MARK_SENT_SQL, Timestamp.from(clock.instant()), row.id());
                    count++;
                } catch (RuntimeException e) {
                    jdbcTemplate.update(MARK_FAILED_SQL, truncate(e.getMessage()), row.id());
                    log.warn("Outbox message {} could not be published to {}/{}: {}",
                            row.id(), row.exchange(), row.routingKey(), e.getMessage());
                    break;
                }
            }
            return count;
        });
        return sent == null ? 0 : sent;
    }

    void send(OutboxRow row) {
        RabbitTemplate template = rabbitTemplate.get();
        Message message = toMessage(row);
        if (template.getConnectionFactory().isPublisherConfirms()) {
            CorrelationData correlation = new CorrelationData(row.id().toString());
            template.send(row.exchange(), row.routingKey(), message, correlation);
            CorrelationData.Confirm confirm;
            try {
                confirm = correlation.getFuture().get(properties.confirmTimeout().toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for broker confirm", e);
            } catch (Exception e) {
                throw new IllegalStateException("No broker confirm: " + e.getMessage(), e);
            }
            if (!confirm.isAck()) {
                throw new IllegalStateException("Broker rejected message: " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                log.warn("Outbox message {} was not routed to any queue ({}/{})", row.id(), row.exchange(), row.routingKey());
            }
        } else {
            template.send(row.exchange(), row.routingKey(), message);
        }
    }

    Message toMessage(OutboxRow row) {
        MessageProperties properties = new MessageProperties();
        properties.setMessageId(row.id().toString());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        properties.setContentType(row.contentType());
        properties.setContentEncoding(row.contentEncoding());
        if (row.headers() != null && !row.headers().isBlank()) {
            try {
                objectMapper.readValue(row.headers(), HEADERS_TYPE).forEach(properties::setHeader);
            } catch (Exception e) {
                throw new IllegalStateException("Outbox headers could not be read", e);
            }
        }
        return new Message(row.body(), properties);
    }

    private void cleanupIfDue() {
        long now = clock.millis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        lastCleanup = now;
        Instant threshold = clock.instant().minus(properties.retention());
        int deleted = jdbcTemplate.update(DELETE_SENT_SQL, Timestamp.from(threshold));
        if (deleted > 0) {
            log.info("Outbox cleanup removed {} published message(s)", deleted);
        }
    }

    private boolean tableExists() {
        try {
            return Boolean.TRUE.equals(jdbcTemplate.queryForObject(TABLE_EXISTS_SQL, Boolean.class));
        } catch (RuntimeException e) {
            log.warn("Outbox table check failed: {}", e.getMessage());
            return false;
        }
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }

    @Override
    public void stop() {
        running = false;
        active = false;
        ScheduledExecutorService current = executor;
        executor = null;
        if (current != null) {
            current.shutdown();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    record OutboxRow(UUID id, String exchange, String routingKey, byte[] body,
                     String contentType, String contentEncoding, String headers) {
    }
}
