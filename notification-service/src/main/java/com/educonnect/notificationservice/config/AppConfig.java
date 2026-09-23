package com.educonnect.notificationservice.config;

import com.educonnect.common.security.ServiceTokenHttpRequestInterceptor;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {
    @Bean
    @LoadBalanced // Servis isimlerini (CLUB-SERVICE) tanır
    public RestTemplate restTemplate(ServiceTokenHttpRequestInterceptor serviceTokenInterceptor) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add(serviceTokenInterceptor);
        return restTemplate;
    }
}
