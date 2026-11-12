package com.educonnect.common.messaging;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadLetterQueueDeclarerTest {

    private final RabbitListenerEndpointRegistry registry = mock(RabbitListenerEndpointRegistry.class);
    private final AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
    private final DeadLetterQueueDeclarer declarer = new DeadLetterQueueDeclarer(registry, amqpAdmin, DeadLetters.EXCHANGE);

    @Test
    void start_shouldDeclareOneDeadLetterQueuePerListenerQueue() {
        when(registry.getListenerContainers()).thenReturn(List.<MessageListenerContainer>of(
                container("user-profile-creation-queue", "user-delete-queue"),
                container("user-delete-queue", "legacy.dlq")));

        declarer.start();

        ArgumentCaptor<Queue> queues = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin, times(2)).declareQueue(queues.capture());
        assertThat(queues.getAllValues()).extracting(Queue::getName)
                .containsExactly("user-profile-creation-queue.dlq", "user-delete-queue.dlq");
        assertThat(queues.getAllValues()).allMatch(Queue::isDurable);

        ArgumentCaptor<Binding> bindings = ArgumentCaptor.forClass(Binding.class);
        verify(amqpAdmin, times(2)).declareBinding(bindings.capture());
        assertThat(bindings.getAllValues()).extracting(Binding::getExchange).containsOnly(DeadLetters.EXCHANGE);
        assertThat(bindings.getAllValues()).extracting(Binding::getRoutingKey)
                .containsExactly("user-profile-creation-queue", "user-delete-queue");
        assertThat(declarer.isRunning()).isTrue();
    }

    @Test
    void start_withoutListeners_shouldDeclareNothing() {
        when(registry.getListenerContainers()).thenReturn(List.of());

        declarer.start();

        verify(amqpAdmin, never()).declareExchange(any(Exchange.class));
        assertThat(declarer.listenerQueues()).isEqualTo(Set.of());
    }

    @Test
    void start_whenBrokerUnavailable_shouldNotFailStartup() {
        when(registry.getListenerContainers()).thenReturn(List.<MessageListenerContainer>of(container("q")));
        doThrow(new AmqpConnectException(new RuntimeException("down"))).when(amqpAdmin).declareExchange(any(Exchange.class));

        declarer.start();

        assertThat(declarer.isRunning()).isTrue();
    }

    private static SimpleMessageListenerContainer container(String... queues) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer(mock(ConnectionFactory.class));
        container.setQueueNames(queues);
        return container;
    }
}
