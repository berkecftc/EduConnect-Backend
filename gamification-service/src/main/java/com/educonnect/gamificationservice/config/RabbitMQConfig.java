package com.educonnect.gamificationservice.config;

import com.educonnect.gamificationservice.dto.event.GamificationEvent;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String GAMIFICATION_EXCHANGE = "gamification.exchange";
    public static final String GAMIFICATION_POINTS_QUEUE = "gamification.points.queue";
    public static final String GAMIFICATION_ROUTING_PATTERN = "gamification.*.*";

    @Bean
    public TopicExchange gamificationExchange() {
        return new TopicExchange(GAMIFICATION_EXCHANGE);
    }

    @Bean
    public Queue gamificationPointsQueue() {
        return new Queue(GAMIFICATION_POINTS_QUEUE, true);
    }

    @Bean
    public Binding gamificationBinding(Queue gamificationPointsQueue, TopicExchange gamificationExchange) {
        return BindingBuilder.bind(gamificationPointsQueue)
                .to(gamificationExchange)
                .with(GAMIFICATION_ROUTING_PATTERN);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages("*");

        Map<String, Class<?>> idClassMapping = new HashMap<>();
        idClassMapping.put(
                "com.educonnect.authservices.dto.message.GamificationEventMessage",
                GamificationEvent.class
        );
        idClassMapping.put(
                "com.educonnect.postservice.event.GamificationEvent",
                GamificationEvent.class
        );
        classMapper.setIdClassMapping(idClassMapping);
        converter.setClassMapper(classMapper);
        return converter;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(4);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}


