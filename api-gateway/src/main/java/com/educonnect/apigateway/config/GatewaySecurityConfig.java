package com.educonnect.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

        // Bu yapılandırmanın TEK GÖREVİ CSRF'i KAPATMAKTIR
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // CSRF Korumasını KAPAT
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/**").permitAll() // Tüm isteklere izin ver (Filtremiz zaten çalışıyor)
                );

        return http.build();
    }
}