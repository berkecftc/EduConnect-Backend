package com.educonnect.notificationservice.config;

import com.educonnect.common.security.ServiceTokenHttpRequestInterceptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {
    @Bean
    @LoadBalanced // Servis isimlerini (CLUB-SERVICE) tanır
    public RestTemplate restTemplate(RestTemplateBuilder builder, ServiceTokenHttpRequestInterceptor serviceTokenInterceptor) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5))
                .additionalInterceptors(serviceTokenInterceptor)
                .build();
    }
}
