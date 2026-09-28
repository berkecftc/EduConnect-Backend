package com.educonnect.assignmentservice.config;

import feign.Logger;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Feign Client Konfigürasyonu
 */
@Configuration
public class FeignClientConfig {

    /**
     * Feign logging seviyesini DEBUG olarak ayarla
     */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }

    /**
     * Retry stratejisi: Başlangıç 100ms, maksimum 1000ms, 3 denemeler
     */
    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(100, 1000, 3);
    }
}

