package com.educonnect.common.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;

import java.time.Instant;

public class DeadLetterRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterRecoverer.class);
    private static final int MAX_MESSAGE_LENGTH = 500;

    private final AmqpTemplate amqpTemplate;
    private final String exchange;

    public DeadLetterRecoverer(AmqpTemplate amqpTemplate, String exchange) {
        this.amqpTemplate = amqpTemplate;
        this.exchange = exchange;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        MessageProperties properties = message.getMessageProperties();
        String queue = properties.getConsumerQueue();
        if (queue == null) {
            throw new AmqpException("Dead letter queue cannot be resolved; consumer queue is unknown", cause);
        }
        Throwable root = rootCause(cause);
        properties.setHeader(DeadLetters.HEADER_EXCEPTION_CLASS, root.getClass().getName());
        properties.setHeader(DeadLetters.HEADER_EXCEPTION_MESSAGE, truncate(root.getMessage()));
        properties.setHeader(DeadLetters.HEADER_ORIGINAL_QUEUE, queue);
        properties.setHeader(DeadLetters.HEADER_ORIGINAL_EXCHANGE, properties.getReceivedExchange());
        properties.setHeader(DeadLetters.HEADER_ORIGINAL_ROUTING_KEY, properties.getReceivedRoutingKey());
        properties.setHeader(DeadLetters.HEADER_FAILED_AT, Instant.now().toString());

        amqpTemplate.send(exchange, queue, message);
        log.error("Message moved to dead letter queue {}: messageId={}, cause={}",
                DeadLetters.queueFor(queue), properties.getMessageId(), root.getClass().getSimpleName());
    }

    static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof ListenerExecutionFailedException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= MAX_MESSAGE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_MESSAGE_LENGTH);
    }
}
