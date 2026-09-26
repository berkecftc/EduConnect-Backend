package com.educonnect.notificationservice.config;

import com.educonnect.common.security.ServiceTokenHttpRequestInterceptor;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {
    @Bean
    @LoadBalanced // Servis isimlerini (CLUB-SERVICE) tanır
    public RestTemplate restTemplate(ServiceTokenHttpRequestInterceptor serviceTokenInterceptor) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.getInterceptors().add(serviceTokenInterceptor);
        return restTemplate;
    }
}
