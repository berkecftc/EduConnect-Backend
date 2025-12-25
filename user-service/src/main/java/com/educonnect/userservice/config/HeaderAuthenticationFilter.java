package com.educonnect.userservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * API Gateway'den gelen header bilgilerini okuyarak Spring Security context'ine
 * authentication bilgisi ekleyen filter.
 */
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";
    private static final String USER_EMAIL_HEADER = "X-Authenticated-User-Email";
    private static final String USER_ROLES_HEADER = "X-Authenticated-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // API Gateway'den gelen header'ları oku
        String userId = request.getHeader(USER_ID_HEADER);
        String userEmail = request.getHeader(USER_EMAIL_HEADER);
        String rolesHeader = request.getHeader(USER_ROLES_HEADER);

        // Eğer kullanıcı bilgileri varsa Spring Security context'ine ekle
        if (userId != null && userEmail != null && rolesHeader != null) {
            // Rolleri parse et (örn: "ROLE_ADMIN,ROLE_USER" -> List<GrantedAuthority>)
            List<SimpleGrantedAuthority> authorities = Arrays.stream(rolesHeader.split(","))
                    .map(String::trim)
                    .filter(role -> !role.isEmpty())
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            // Authentication objesi oluştur
            // Principal olarak email kullanıyoruz
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userEmail, null, authorities);

            // userId'yi authentication detaylarına ekle
            authentication.setDetails(userId);

            // Security context'e set et
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // Bir sonraki filter'a devam et
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Actuator endpoint'lerine filter uygulanmasın
        String path = request.getRequestURI();
        return path.startsWith("/actuator/");
    }
}

