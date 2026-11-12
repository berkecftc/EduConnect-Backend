package com.educonnect.gamificationservice;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRabbit
@EnableScheduling
@EnableFeignClients
public class GamificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GamificationServiceApplication.class, args);
    }
}

