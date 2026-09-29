package com.educonnect.common.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.rabbitmq.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class RabbitTestContainer {

    public static final DockerImageName IMAGE = DockerImageName.parse("rabbitmq:3.13.7-management")
            .asCompatibleSubstituteFor("rabbitmq");

    @Bean
    @ServiceConnection
    public RabbitMQContainer rabbitContainer() {
        return new RabbitMQContainer(IMAGE);
    }
}
