package com.educonnect.userservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

    @Bean(destroyMethod = "close")
    public Executor profileAggregationExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

