package com.educonnect.eventservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new LoggingCacheErrorHandler();
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJacksonJsonRedisSerializer serializer = cacheValueSerializer();

        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5)) // TTL: 5 dakika
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();
    }

    static GenericJacksonJsonRedisSerializer cacheValueSerializer() {
        JsonMapper objectMapper = JsonMapper.builder()
                .activateDefaultTyping(
                        cacheTypeValidator(),
                        DefaultTyping.NON_FINAL,
                        JsonTypeInfo.As.PROPERTY)
                .build();
        return new GenericJacksonJsonRedisSerializer(objectMapper);
    }

    static PolymorphicTypeValidator cacheTypeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.educonnect.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();
    }
}

