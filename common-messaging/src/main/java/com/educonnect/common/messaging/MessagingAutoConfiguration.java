package com.educonnect.common.messaging;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.config.RabbitListenerConfigUtils;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

@AutoConfiguration(after = RabbitAutoConfiguration.class)
@ConditionalOnClass(RabbitListenerEndpointRegistry.class)
@ConditionalOnProperty(prefix = "educonnect.messaging.retry", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingAutoConfiguration {

    @Bean
    public static ListenerRetryConfigurer listenerRetryConfigurer(Environment environment,
                                                                  ObjectProvider<AmqpTemplate> amqpTemplate) {
        MessagingProperties properties = Binder.get(environment)
                .bindOrCreate("educonnect.messaging.retry", MessagingProperties.class);
        return new ListenerRetryConfigurer(properties, amqpTemplate);
    }

    @Bean
    @ConditionalOnBean(AmqpAdmin.class)
    public DeadLetterQueueDeclarer deadLetterQueueDeclarer(
            @Qualifier(RabbitListenerConfigUtils.RABBIT_LISTENER_ENDPOINT_REGISTRY_BEAN_NAME) RabbitListenerEndpointRegistry registry,
            AmqpAdmin amqpAdmin,
            MessagingProperties properties) {
        return new DeadLetterQueueDeclarer(registry, amqpAdmin, properties.deadLetterExchange());
    }
}
