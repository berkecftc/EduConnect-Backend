package com.educonnect.common.messaging;

import com.educonnect.common.messaging.dedup.DuplicateMessageFilter;
import com.educonnect.common.messaging.dedup.ProcessedMessageStore;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.retry.RetryPolicy;

import java.util.List;

public class ListenerRetryConfigurer implements BeanPostProcessor {

    private static final List<Class<? extends Throwable>> PERMANENT_FAILURES = List.of(
            AmqpRejectAndDontRequeueException.class,
            org.springframework.amqp.support.converter.MessageConversionException.class,
            org.springframework.messaging.converter.MessageConversionException.class,
            org.springframework.messaging.handler.invocation.MethodArgumentResolutionException.class);

    private final MessagingProperties properties;
    private final ObjectProvider<AmqpTemplate> amqpTemplate;
    private final ObjectProvider<ProcessedMessageStore> processedMessageStore;

    public ListenerRetryConfigurer(MessagingProperties properties,
                                   ObjectProvider<AmqpTemplate> amqpTemplate,
                                   ObjectProvider<ProcessedMessageStore> processedMessageStore) {
        this.properties = properties;
        this.amqpTemplate = amqpTemplate;
        this.processedMessageStore = processedMessageStore;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (properties.enabled() && bean instanceof AbstractRabbitListenerContainerFactory<?> factory) {
            factory.setDefaultRequeueRejected(false);
            factory.setObservationEnabled(true);
            if (factory.getAdviceChain() == null || factory.getAdviceChain().length == 0) {
                factory.setAdviceChain(
                        RetryInterceptorBuilder.stateless()
                                .retryPolicy(retryPolicy(properties))
                                .recoverer(recoverer())
                                .build(),
                        new DuplicateMessageFilter(processedMessageStore));
            }
        }
        return bean;
    }

    static RetryPolicy retryPolicy(MessagingProperties properties) {
        return RetryPolicy.builder()
                .maxRetries(Math.max(0, properties.maxAttempts() - 1))
                .delay(properties.initialInterval())
                .multiplier(Math.max(1.0, properties.multiplier()))
                .maxDelay(properties.maxInterval())
                .predicate(failure -> !isPermanent(failure))
                .build();
    }

    static boolean isPermanent(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            for (Class<? extends Throwable> type : PERMANENT_FAILURES) {
                if (type.isInstance(current)) {
                    return true;
                }
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return false;
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
