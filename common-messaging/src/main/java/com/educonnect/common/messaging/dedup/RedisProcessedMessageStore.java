package com.educonnect.common.messaging.dedup;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

public class RedisProcessedMessageStore implements ProcessedMessageStore {

    static final String KEY_PREFIX = "educonnect:processed:";

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public RedisProcessedMessageStore(StringRedisTemplate redisTemplate, Duration ttl) {
        this.redisTemplate = redisTemplate;
        this.ttl = ttl;
    }

    @Override
    public boolean isProcessed(String queue, String messageId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(queue, messageId)));
    }

    @Override
    public void markProcessed(String queue, String messageId) {
        redisTemplate.opsForValue().set(key(queue, messageId), "1", ttl);
    }

    static String key(String queue, String messageId) {
        return KEY_PREFIX + queue + ":" + messageId;
    }
}
