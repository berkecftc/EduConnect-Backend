package com.educonnect.assignmentservice.config;

import feign.Logger;
import feign.Retryer;
import feign.codec.ErrorDecoder;
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
        return Logger.Level.FULL; // FULL, HEADERS, BASIC, NONE
    }

    /**
     * Retry stratejisi: Başlangıç 100ms, maksimum 1000ms, 3 denemeler
     */
    @Bean
    public Retryer feignRetryer() {
        return new Retryer.Default(100, 1000, 3);
    }

    /**
     * Custom error decoder (isteğe bağlı)
     */
    @Bean
    public ErrorDecoder errorDecoder() {
        return (methodKey, response) -> {
            if (response.status() >= 400) {
                throw new RuntimeException("Feign Client Error: " + response.status() + " - " + response.reason());
            }
            return new RuntimeException("Unknown error");
        };
    }
}

