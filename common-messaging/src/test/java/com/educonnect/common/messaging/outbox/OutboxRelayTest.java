package com.educonnect.common.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
    private final OutboxRelay relay = new OutboxRelay(mock(JdbcTemplate.class), mock(TransactionTemplate.class),
            () -> rabbitTemplate, new ObjectMapper(),
            new OutboxProperties(true, Duration.ofSeconds(2), 50, Duration.ofMillis(200), Duration.ofDays(7)),
            Clock.systemUTC());

    private final OutboxRelay.OutboxRow row = new OutboxRelay.OutboxRow(
            UUID.fromString("5eed0000-0000-4000-8000-000000000001"), "user-exchange", "user.registered",
            "{\"a\":1}".getBytes(StandardCharsets.UTF_8), "application/json", "UTF-8",
            "{\"__TypeId__\":\"com.example.Payload\"}");

    @Test
    void toMessage_shouldRestoreBodyHeadersAndMessageId() {
        Message message = relay.toMessage(row);

        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(row.id().toString());
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getContentEncoding()).isEqualTo("UTF-8");
        assertThat((String) message.getMessageProperties().getHeader("__TypeId__")).isEqualTo("com.example.Payload");
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    }

    @Test
    void send_withoutConfirms_shouldSendToStoredExchangeAndRoutingKey() {
        when(rabbitTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.isPublisherConfirms()).thenReturn(false);

        relay.send(row);

        verify(rabbitTemplate).send(eq("user-exchange"), eq("user.registered"), any(Message.class));
    }

    @Test
    void send_withConfirms_whenBrokerAcks_shouldSucceed() {
        when(rabbitTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.isPublisherConfirms()).thenReturn(true);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbitTemplate).send(eq("user-exchange"), eq("user.registered"), any(Message.class), any(CorrelationData.class));

        relay.send(row);
    }

    @Test
    void send_withConfirms_whenBrokerNacks_shouldFailSoRowStaysPending() {
        when(rabbitTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.isPublisherConfirms()).thenReturn(true);
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "queue full"));
            return null;
        }).when(rabbitTemplate).send(eq("user-exchange"), eq("user.registered"), any(Message.class), any(CorrelationData.class));

        assertThatThrownBy(() -> relay.send(row)).hasMessageContaining("queue full");
    }

    @Test
    void send_withConfirms_whenNoConfirmArrives_shouldFail() {
        when(rabbitTemplate.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.isPublisherConfirms()).thenReturn(true);

        assertThatThrownBy(() -> relay.send(row)).hasMessageContaining("No broker confirm");
    }
}
