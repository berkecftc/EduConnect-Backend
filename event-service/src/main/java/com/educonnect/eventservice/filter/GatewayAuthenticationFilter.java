package com.educonnect.eventservice.filter; // Paket ismine dikkat!

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        log.info("GatewayAuthenticationFilter processing request: {} {}", request.getMethod(), path);

        // Gateway'in eklediği başlıkları oku
        String userEmail = request.getHeader("X-Authenticated-User-Email");
        String userId = request.getHeader("X-Authenticated-User-Id");
        String userRoles = request.getHeader("X-Authenticated-User-Roles");

        log.info("Headers received - Email: {}, UserId: {}, Roles: {}", userEmail, userId, userRoles);

        // Eğer başlıklar varsa (Gateway'den geçmişse), içeri al
        if (userEmail != null && userRoles != null) {
            log.debug("Processing authentication from Gateway: email={}, roles={}", userEmail, userRoles);

            // Rolleri ayır ve "ROLE_" öneki kontrolü yap
            List<SimpleGrantedAuthority> authorities = Arrays.stream(userRoles.split(","))
                    .map(String::trim)
                    .filter(r -> !r.isEmpty())
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role) // Prefix garantisi
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            log.info("Authorities created: {}", authorities);

            // Spring Security Context'ini doldur
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userEmail, // Principal
                            userId,    // Credentials yerine ID'yi koyabiliriz (veya null)
                            authorities
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.info("SecurityContext set with authentication for user: {}", userEmail);
        } else {
            log.warn("No authentication headers found for request: {} {}", request.getMethod(), path);
        }

        filterChain.doFilter(request, response);
    }
}