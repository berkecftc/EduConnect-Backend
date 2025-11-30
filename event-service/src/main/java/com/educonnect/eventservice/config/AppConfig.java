package com.educonnect.eventservice.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    @Bean
    @LoadBalanced // Bu anotasyon kritik! Servis ismini (CLUB-SERVICE) IP'ye çevirir.
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}