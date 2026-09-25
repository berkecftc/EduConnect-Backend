package com.educonnect.common.messaging.dedup;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnBean(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "educonnect.messaging.dedup", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(DeduplicationAutoConfiguration.DeduplicationProperties.class)
public class DeduplicationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ProcessedMessageStore processedMessageStore(StringRedisTemplate redisTemplate,
                                                       DeduplicationProperties properties) {
        return new RedisProcessedMessageStore(redisTemplate, properties.ttl());
    }

    @ConfigurationProperties(prefix = "educonnect.messaging.dedup")
    public record DeduplicationProperties(@DefaultValue("true") boolean enabled,
                                          @DefaultValue("24h") Duration ttl) {
    }
}
