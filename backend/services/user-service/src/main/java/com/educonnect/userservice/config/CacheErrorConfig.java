package com.educonnect.userservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheErrorConfig implements CachingConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheErrorConfig.class);

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                LOGGER.warn("Cache GET error. cache={}, key={}, reason={}", cache.getName(), key, exception.getMessage());
                // Cache bozuk olsa bile request akışı devam etsin.
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                LOGGER.warn("Cache PUT error. cache={}, key={}, reason={}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                LOGGER.warn("Cache EVICT error. cache={}, key={}, reason={}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                LOGGER.warn("Cache CLEAR error. cache={}, reason={}", cache.getName(), exception.getMessage());
            }
        };
    }
}


