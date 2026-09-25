package com.educonnect.clubservice.config;

import com.educonnect.common.security.ServiceIdentity;
import com.educonnect.common.security.VerifiedIdentityFilter;
import jakarta.servlet.DispatcherType;
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
@EnableMethodSecurity // @PreAuthorize anotasyonlarını (Rol kontrolü) aktif etmek için
public class SecurityConfig {

    private final VerifiedIdentityFilter verifiedIdentityFilter;

    public SecurityConfig(VerifiedIdentityFilter verifiedIdentityFilter) {
        this.verifiedIdentityFilter = verifiedIdentityFilter;
    }



    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API'ler için CSRF'i kapat
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // JWT kullandığımız için

                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/*/internal/**").hasRole(ServiceIdentity.ROLE)

                        // Dashboard endpoints - authentication required (bu kurallar önce gelmeli!)
                        .requestMatchers(HttpMethod.GET, "/api/clubs/my-memberships").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/clubs/my-managed-clubs").authenticated()

                        // Public GET endpoints (genel kulüp listeleme/detay)
                        .requestMatchers(HttpMethod.GET, "/api/clubs").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/clubs/{clubId}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/clubs/{clubId}/board-members").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/clubs/search").permitAll()

                        // Akademisyen (Danışman) endpoints - görev değişikliği onay/red
                        .requestMatchers(HttpMethod.GET, "/api/academician/role-change-requests").hasRole("ACADEMICIAN")
                        .requestMatchers(HttpMethod.PUT, "/api/academician/role-change-requests/*/approve").hasRole("ACADEMICIAN")
                        .requestMatchers(HttpMethod.PUT, "/api/academician/role-change-requests/*/reject").hasRole("ACADEMICIAN")
                        .requestMatchers(HttpMethod.GET, "/api/academician/clubs/*/role-change-requests/count").hasRole("ACADEMICIAN")
                        .requestMatchers("/api/academician/**").hasRole("ACADEMICIAN")

                        // Admin endpoints (requires ADMIN role via @PreAuthorize)
                        .requestMatchers("/api/admin/clubs/**").authenticated()

                        // Other club endpoints require authentication
                        .requestMatchers("/api/clubs/**").authenticated()

                        .anyRequest().authenticated()
                )
                // Gateway'den gelen header'ları okuyup Security context'ine ekle
                .addFilterBefore(verifiedIdentityFilter, UsernamePasswordAuthenticationFilter.class)

                // Form login ve HTTP Basic'i devre dışı bırak (API Gateway üzerinden JWT kullanıyoruz)
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());


        return http.build();
    }
}