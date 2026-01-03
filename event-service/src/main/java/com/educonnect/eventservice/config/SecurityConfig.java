package com.educonnect.eventservice.config;

import com.educonnect.eventservice.filter.GatewayAuthenticationFilter; // YENİ FİLTRE
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

    public SecurityConfig(GatewayAuthenticationFilter gatewayAuthenticationFilter) {
        this.gatewayAuthenticationFilter = gatewayAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Dashboard endpoints - authentication required (bu kurallar önce gelmeli!)
                        .requestMatchers(HttpMethod.GET, "/api/events/my-registrations").authenticated()

                        // Club Official/Admin management endpoints (POST, GET, etc.)
                        .requestMatchers(HttpMethod.POST, "/api/events/manage").hasAnyRole("ADMIN", "CLUB_OFFICIAL")
                        .requestMatchers(HttpMethod.GET, "/api/events/manage/**").hasAnyRole("ADMIN", "CLUB_OFFICIAL")
                        .requestMatchers("/api/events/manage/**").hasAnyRole("ADMIN", "CLUB_OFFICIAL")


                        // Event registration requires authentication
                        .requestMatchers("/api/events/*/register").authenticated()

                        // Public GET endpoints (etkinlik listeleme/detay)
                        .requestMatchers(HttpMethod.GET, "/api/events").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/events/{eventId}").permitAll()

                        .anyRequest().authenticated()
                )
                // YENİ FİLTREYİ EKLE
                .addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // Form login ve HTTP Basic'i devre dışı bırak (API Gateway üzerinden JWT kullanıyoruz)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }
}