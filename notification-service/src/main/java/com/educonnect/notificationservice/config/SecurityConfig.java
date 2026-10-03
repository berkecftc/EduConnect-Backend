package com.educonnect.notificationservice.config;

import com.educonnect.common.security.VerifiedIdentityFilter;
import com.educonnect.common.web.ProblemSecurityHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   VerifiedIdentityFilter verifiedIdentityFilter,
                                                   ProblemSecurityHandlers problemSecurityHandlers) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/notifications/unsubscribe").permitAll()
                        .requestMatchers("/api/notifications/**").authenticated()
                        .anyRequest().denyAll())
                .addFilterBefore(verifiedIdentityFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(problemSecurityHandlers)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }
}
