package com.educonnect.common.messaging;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DeadLetterRecovererTest {

    private final AmqpTemplate amqpTemplate = mock(AmqpTemplate.class);
    private final DeadLetterRecoverer recoverer = new DeadLetterRecoverer(amqpTemplate, DeadLetters.EXCHANGE);

    @Test
    void recover_shouldRouteToConsumerQueueWithFailureHeaders() {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue("user-profile-creation-queue");
        properties.setReceivedExchange("user-exchange");
        properties.setReceivedRoutingKey("user.registered");
        Message message = new Message("{}".getBytes(), properties);
        IllegalStateException root = new IllegalStateException("database down");

        recoverer.recover(message, new ListenerExecutionFailedException("failed", root, message));

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(amqpTemplate).send(eq(DeadLetters.EXCHANGE), eq("user-profile-creation-queue"), sent.capture());
        MessageProperties headers = sent.getValue().getMessageProperties();
        assertThat((String) headers.getHeader(DeadLetters.HEADER_EXCEPTION_CLASS)).isEqualTo(IllegalStateException.class.getName());
        assertThat((String) headers.getHeader(DeadLetters.HEADER_EXCEPTION_MESSAGE)).isEqualTo("database down");
        assertThat((String) headers.getHeader(DeadLetters.HEADER_ORIGINAL_QUEUE)).isEqualTo("user-profile-creation-queue");
        assertThat((String) headers.getHeader(DeadLetters.HEADER_ORIGINAL_EXCHANGE)).isEqualTo("user-exchange");
        assertThat((String) headers.getHeader(DeadLetters.HEADER_ORIGINAL_ROUTING_KEY)).isEqualTo("user.registered");
        assertThat((String) headers.getHeader(DeadLetters.HEADER_FAILED_AT)).isNotBlank();
    }

    @Test
    void recover_shouldTruncateLongExceptionMessages() {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue("q");
        Message message = new Message(new byte[0], properties);

        recoverer.recover(message, new RuntimeException("x".repeat(2000)));

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(amqpTemplate).send(anyString(), anyString(), sent.capture());
        assertThat((String) sent.getValue().getMessageProperties().getHeader(DeadLetters.HEADER_EXCEPTION_MESSAGE)).hasSize(500);
    }

    @Test
    void recover_withoutConsumerQueue_shouldFailInsteadOfDroppingMessage() {
        Message message = new Message(new byte[0], new MessageProperties());

        assertThatThrownBy(() -> recoverer.recover(message, new RuntimeException("boom")))
                .isInstanceOf(AmqpException.class);
        verify(amqpTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }
}
