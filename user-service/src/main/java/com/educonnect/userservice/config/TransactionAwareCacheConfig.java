package com.educonnect.userservice.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransactionAwareCacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer transactionAwareCacheManager() {
        return builder -> builder.transactionAware();
    }
}
