package com.educonnect.postservice.config;

import com.educonnect.common.security.ServiceIdentity;
import com.educonnect.common.security.VerifiedIdentityFilter;
import com.educonnect.common.web.ProblemSecurityHandlers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final VerifiedIdentityFilter verifiedIdentityFilter;
    private final ProblemSecurityHandlers problemSecurityHandlers;

    public SecurityConfig(VerifiedIdentityFilter verifiedIdentityFilter,
                          ProblemSecurityHandlers problemSecurityHandlers) {
        this.verifiedIdentityFilter = verifiedIdentityFilter;
        this.problemSecurityHandlers = problemSecurityHandlers;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/*/internal/**").hasRole(ServiceIdentity.ROLE)
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().permitAll() // API Gateway zaten authentication yapıyor
                )
                .addFilterBefore(verifiedIdentityFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(problemSecurityHandlers)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());

        return http.build();
    }
}

