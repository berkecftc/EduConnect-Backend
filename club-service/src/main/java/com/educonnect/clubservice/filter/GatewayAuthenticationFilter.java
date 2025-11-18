package com.educonnect.clubservice.filter;

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

/**
 * Gateway'den gelen X-Authenticated-User-Role header'ını okuyup
 * Spring Security context'ine authorities olarak ekler.
 */
@Component
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userEmail = request.getHeader("X-Authenticated-User-Email");
        String userId = request.getHeader("X-Authenticated-User-Id");
        String userRoles = request.getHeader("X-Authenticated-User-Roles");

        if (userEmail != null && userRoles != null) {
            log.debug("Processing authentication from Gateway: email={}, userId={}, roles={}",
                     userEmail, userId, userRoles);

            // Comma-separated roles'ü parse et ve her birine ROLE_ prefix ekle
            var authorities = java.util.Arrays.stream(userRoles.split(","))
                    .map(String::trim)
                    .filter(r -> !r.isEmpty())
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    userEmail,
                    null,
                    authorities
                );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authentication set in SecurityContext with authorities: {}", authorities);
        }

        filterChain.doFilter(request, response);
    }
}
