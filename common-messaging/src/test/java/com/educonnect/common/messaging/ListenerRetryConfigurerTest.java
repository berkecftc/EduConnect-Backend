package com.educonnect.common.messaging;

import org.aopalliance.aop.Advice;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.retry.support.RetryTemplate;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ListenerRetryConfigurerTest {

    private final MessagingProperties properties = new MessagingProperties(
            true, 3, Duration.ofMillis(1), 1.0, Duration.ofMillis(1), DeadLetters.EXCHANGE);

    @Test
    void factory_shouldGetRetryAdviceAndNoRequeue() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

        configurer(properties).postProcessAfterInitialization(factory, "rabbitListenerContainerFactory");

        assertThat(factory.getAdviceChain()).hasSize(1);
    }

    @Test
    void factory_withExistingAdvice_shouldKeepIt() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        Advice existing = mock(Advice.class);
        factory.setAdviceChain(existing);

        configurer(properties).postProcessAfterInitialization(factory, "custom");

        assertThat(factory.getAdviceChain()).containsExactly(existing);
    }

    @Test
    void disabled_shouldLeaveFactoryUntouched() {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        MessagingProperties disabled = new MessagingProperties(
                false, 3, Duration.ofMillis(1), 1.0, Duration.ofMillis(1), DeadLetters.EXCHANGE);

        configurer(disabled).postProcessAfterInitialization(factory, "rabbitListenerContainerFactory");

        assertThat(factory.getAdviceChain()).isNull();
    }

    @Test
    void transientFailure_shouldBeRetriedUpToMaxAttempts() {
        RetryTemplate template = ListenerRetryConfigurer.retryTemplate(properties);
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> template.execute(context -> {
            calls.incrementAndGet();
            throw wrapped(new IllegalStateException("database down"));
        })).isInstanceOf(ListenerExecutionFailedException.class);

        assertThat(calls).hasValue(3);
    }

    @Test
    void permanentFailure_shouldNotBeRetried() {
        RetryTemplate template = ListenerRetryConfigurer.retryTemplate(properties);
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> template.execute(context -> {
            calls.incrementAndGet();
            throw wrapped(new AmqpRejectAndDontRequeueException("invalid payload"));
        })).isInstanceOf(ListenerExecutionFailedException.class);

        assertThat(calls).hasValue(1);
    }

    @SuppressWarnings("unchecked")
    private static ListenerRetryConfigurer configurer(MessagingProperties properties) {
        return new ListenerRetryConfigurer(properties, mock(ObjectProvider.class));
    }

    private static ListenerExecutionFailedException wrapped(Throwable cause) {
        return new ListenerExecutionFailedException("failed", cause, new Message(new byte[0], new MessageProperties()));
    }
}
