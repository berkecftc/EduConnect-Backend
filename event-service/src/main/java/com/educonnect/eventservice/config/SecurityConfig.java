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
                        // ===== ACADEMICIAN (Advisor) endpoints - EN ÖNCE! =====
                        .requestMatchers(HttpMethod.GET, "/api/events/advisor/**").hasRole("ACADEMICIAN")
                        .requestMatchers(HttpMethod.POST, "/api/events/advisor/**").hasRole("ACADEMICIAN")
                        .requestMatchers("/api/events/advisor/**").hasRole("ACADEMICIAN")

                        // Dashboard endpoints - authentication required
                        .requestMatchers(HttpMethod.GET, "/api/events/my-registrations").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/events/my-participation-requests").authenticated()

                        // Club events for students (authenticated)
                        .requestMatchers(HttpMethod.GET, "/api/events/club/**").authenticated()

                        // Participation request endpoints (authenticated)
                        .requestMatchers(HttpMethod.POST, "/api/events/*/participation-request").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/events/*/participation-requests/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/events/participation-requests/*/approve").hasAnyRole("ADMIN", "CLUB_OFFICIAL")
                        .requestMatchers(HttpMethod.POST, "/api/events/participation-requests/*/reject").hasAnyRole("ADMIN", "CLUB_OFFICIAL")
                        .requestMatchers(HttpMethod.GET, "/api/events/official/pending-requests").hasAnyRole("ADMIN", "CLUB_OFFICIAL")

                        // Club Official/Admin management endpoints
                        .requestMatchers(HttpMethod.POST, "/api/events/manage").hasAnyRole("ADMIN", "CLUB_OFFICIAL")
                        .requestMatchers(HttpMethod.GET, "/api/events/manage/pending").hasRole("ACADEMICIAN")
                        .requestMatchers(HttpMethod.POST, "/api/events/manage/*/approve").hasRole("ACADEMICIAN")
                        .requestMatchers(HttpMethod.POST, "/api/events/manage/*/reject").hasRole("ACADEMICIAN")
                        .requestMatchers(HttpMethod.GET, "/api/events/manage/**").hasAnyRole("ADMIN", "CLUB_OFFICIAL", "ACADEMICIAN")
                        .requestMatchers("/api/events/manage/**").hasAnyRole("ADMIN", "CLUB_OFFICIAL", "ACADEMICIAN")

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