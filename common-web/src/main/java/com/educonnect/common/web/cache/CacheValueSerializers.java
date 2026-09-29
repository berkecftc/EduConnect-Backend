package com.educonnect.common.web.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

public final class CacheValueSerializers {

    private CacheValueSerializers() {
    }

    public static GenericJacksonJsonRedisSerializer typed() {
        JsonMapper mapper = JsonMapper.builder()
                .activateDefaultTyping(typeValidator(), DefaultTyping.NON_FINAL_AND_RECORDS, JsonTypeInfo.As.PROPERTY)
                .build();
        return new GenericJacksonJsonRedisSerializer(mapper);
    }

    static PolymorphicTypeValidator typeValidator() {
        return BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.educonnect.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .build();
    }
}
