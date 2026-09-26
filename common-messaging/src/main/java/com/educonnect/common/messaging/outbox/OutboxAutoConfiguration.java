package com.educonnect.common.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@AutoConfiguration(after = {
        RabbitAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        TransactionAutoConfiguration.class,
        JdbcTemplateAutoConfiguration.class
})
@ConditionalOnClass({JdbcTemplate.class, ObjectMapper.class, RabbitTemplate.class})
@ConditionalOnBean({JdbcTemplate.class, PlatformTransactionManager.class, RabbitTemplate.class})
@ConditionalOnProperty(prefix = "educonnect.messaging.outbox", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    private static final ObjectMapper HEADER_MAPPER = new ObjectMapper();

    @Bean
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(JdbcTemplate jdbcTemplate,
                                   PlatformTransactionManager transactionManager,
                                   ObjectProvider<RabbitTemplate> rabbitTemplate,
                                   OutboxProperties properties) {
        return new OutboxRelay(jdbcTemplate, new TransactionTemplate(transactionManager),
                rabbitTemplate::getObject, HEADER_MAPPER, properties, Clock.systemUTC());
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisher outboxPublisher(JdbcTemplate jdbcTemplate,
                                           ObjectProvider<RabbitTemplate> rabbitTemplate,
                                           OutboxRelay outboxRelay) {
        return new OutboxPublisher(jdbcTemplate, () -> rabbitTemplate.getObject().getMessageConverter(),
                HEADER_MAPPER, outboxRelay::trigger, Clock.systemUTC());
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.core.instrument.binder.MeterBinder")
    static class OutboxMetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public OutboxMetrics outboxMetrics(JdbcTemplate jdbcTemplate) {
            return new OutboxMetrics(jdbcTemplate);
        }
    }
}
