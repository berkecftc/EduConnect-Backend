package com.educonnect.courseservice.config;

import com.educonnect.common.security.ServiceIdentity;
import com.educonnect.common.security.VerifiedIdentityFilter;
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

    public SecurityConfig(VerifiedIdentityFilter verifiedIdentityFilter) {
        this.verifiedIdentityFilter = verifiedIdentityFilter;
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
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable());

        return http.build();
    }
}

