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
        corsConfig.setAllowedOrigins(Arrays.asList("http://localhost:5173", "http://localhost:3000"));

        // İzin verilen HTTP metotları
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // İzin verilen başlıklar (Token ve JSON için)
        corsConfig.setAllowedHeaders(Arrays.asList("*")); // Tüm header'lara izin ver

        // Frontend'in okuyabileceği response header'ları
        corsConfig.setExposedHeaders(Arrays.asList(
                "Authorization",
                "X-Authenticated-User-Id",
                "X-Authenticated-User-Email",
                "X-Authenticated-User-Roles"
        ));

        // Kimlik bilgilerine (Cookies, Authorization header) izin ver
        corsConfig.setAllowCredentials(true);

        // Preflight cache süresi (saniye)
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);

        return new CorsWebFilter(source);
    }
}