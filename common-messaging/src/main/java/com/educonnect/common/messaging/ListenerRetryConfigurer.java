package com.educonnect.common.messaging;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Map;

public class ListenerRetryConfigurer implements BeanPostProcessor {

    private final MessagingProperties properties;
    private final ObjectProvider<AmqpTemplate> amqpTemplate;

    public ListenerRetryConfigurer(MessagingProperties properties, ObjectProvider<AmqpTemplate> amqpTemplate) {
        this.properties = properties;
        this.amqpTemplate = amqpTemplate;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (properties.enabled() && bean instanceof AbstractRabbitListenerContainerFactory<?> factory) {
            factory.setDefaultRequeueRejected(false);
            if (factory.getAdviceChain() == null || factory.getAdviceChain().length == 0) {
                factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                        .retryOperations(retryTemplate(properties))
                        .recoverer(recoverer())
                        .build());
            }
        }
        return bean;
    }

    static RetryTemplate retryTemplate(MessagingProperties properties) {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(Math.max(1, properties.maxAttempts()), Map.of(
                AmqpRejectAndDontRequeueException.class, false,
                org.springframework.amqp.support.converter.MessageConversionException.class, false,
                org.springframework.messaging.converter.MessageConversionException.class, false,
                org.springframework.messaging.handler.invocation.MethodArgumentResolutionException.class, false
        ), true, true);

        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(properties.initialInterval().toMillis());
        backOff.setMultiplier(properties.multiplier());
        backOff.setMaxInterval(properties.maxInterval().toMillis());

        RetryTemplate template = new RetryTemplate();
        template.setRetryPolicy(retryPolicy);
        template.setBackOffPolicy(backOff);
        return template;
    }

    private MessageRecoverer recoverer() {
        return new MessageRecoverer() {
            private volatile DeadLetterRecoverer delegate;

            @Override
            public void recover(Message message, Throwable cause) {
                DeadLetterRecoverer current = delegate;
                if (current == null) {
                    current = new DeadLetterRecoverer(amqpTemplate.getObject(), properties.deadLetterExchange());
                    delegate = current;
                }
                current.recover(message, cause);
            }
        };
    }
}
