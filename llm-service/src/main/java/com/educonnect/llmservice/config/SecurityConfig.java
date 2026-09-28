package com.educonnect.llmservice.config;

import com.educonnect.common.security.ServiceIdentity;
import com.educonnect.common.security.VerifiedIdentityFilter;
import com.educonnect.common.web.ProblemSecurityHandlers;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
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
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/*/internal/**").hasRole(ServiceIdentity.ROLE)
                        .requestMatchers("/api/ai/instructor-copilot").hasRole("ACADEMICIAN")
                        .requestMatchers("/api/ai/student-assistant").hasRole("STUDENT")
                        .anyRequest().permitAll())
                .addFilterBefore(verifiedIdentityFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(problemSecurityHandlers)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }
}
