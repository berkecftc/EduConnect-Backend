package com.educonnect.common.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class RedisTestContainer {

    public static final DockerImageName IMAGE = DockerImageName.parse(
            "redis/redis-stack@sha256:5d5154123426693540ea6c1d9e638eae2bf2024879c839db6fcfdd7717e817c7");

    @Bean
    @ServiceConnection(name = "redis")
    public GenericContainer<?> redisContainer() {
        return new GenericContainer<>(IMAGE).withExposedPorts(6379);
    }
}
