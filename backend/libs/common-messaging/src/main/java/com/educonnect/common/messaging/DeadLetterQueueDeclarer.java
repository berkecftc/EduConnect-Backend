package com.educonnect.common.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.context.SmartLifecycle;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public class DeadLetterQueueDeclarer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterQueueDeclarer.class);

    private final RabbitListenerEndpointRegistry registry;
    private final AmqpAdmin amqpAdmin;
    private final String exchangeName;
    private volatile boolean running;

    public DeadLetterQueueDeclarer(RabbitListenerEndpointRegistry registry, AmqpAdmin amqpAdmin, String exchangeName) {
        this.registry = registry;
        this.amqpAdmin = amqpAdmin;
        this.exchangeName = exchangeName;
    }

    @Override
    public void start() {
        Set<String> queues = listenerQueues();
        if (!queues.isEmpty()) {
            try {
                DirectExchange exchange = new DirectExchange(exchangeName, true, false);
                amqpAdmin.declareExchange(exchange);
                for (String queue : queues) {
                    Queue deadLetterQueue = QueueBuilder.durable(DeadLetters.queueFor(queue)).build();
                    amqpAdmin.declareQueue(deadLetterQueue);
                    Binding binding = BindingBuilder.bind(deadLetterQueue).to(exchange).with(queue);
                    amqpAdmin.declareBinding(binding);
                }
                log.info("Dead letter queues ready for {} listener queue(s) on exchange {}", queues.size(), exchangeName);
            } catch (AmqpException e) {
                log.warn("Dead letter queues could not be declared: {}", e.getMessage());
            }
        }
        running = true;
    }

    Set<String> listenerQueues() {
        Set<String> queues = new LinkedHashSet<>();
        registry.getListenerContainers().forEach(container -> {
            if (container instanceof AbstractMessageListenerContainer listenerContainer) {
                queues.addAll(Arrays.asList(listenerContainer.getQueueNames()));
            }
        });
        queues.removeIf(queue -> queue == null || queue.isBlank() || queue.endsWith(DeadLetters.QUEUE_SUFFIX));
        return queues;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return 0;
    }
}
