package com.educonnect.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration corsConfig = new CorsConfiguration();

        // Frontend adresine izin ver (React varsayılan portu)
        corsConfig.setAllowedOrigins(Arrays.asList("http://localhost:5173"));

        // İzin verilen HTTP metotları
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // İzin verilen başlıklar (Token ve JSON için)
        corsConfig.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Authenticated-User-Id"));

        // Kimlik bilgilerine (Cookies, Authorization header) izin ver
        corsConfig.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}