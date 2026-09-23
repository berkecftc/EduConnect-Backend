package com.educonnect.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VerifiedIdentityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(VerifiedIdentityFilter.class);

    private final JwtDecoder jwtDecoder;
    private final String internalAudience;

    public VerifiedIdentityFilter(JwtDecoder jwtDecoder) {
        this(jwtDecoder, ServiceIdentity.DEFAULT_INTERNAL_AUDIENCE);
    }

    public VerifiedIdentityFilter(JwtDecoder jwtDecoder, String internalAudience) {
        this.jwtDecoder = jwtDecoder;
        this.internalAudience = internalAudience;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            filterChain.doFilter(new VerifiedIdentityRequestWrapper(request, Map.of()), response);
            return;
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            log.debug("Rejected JWT on {} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());
            filterChain.doFilter(new VerifiedIdentityRequestWrapper(request, Map.of()), response);
            return;
        }

        if (isServiceToken(jwt)) {
            if (ServiceIdentity.INTERNAL_PATH.matcher(request.getRequestURI()).matches()) {
                authenticateService(jwt);
            } else {
                log.debug("Ignored service token from '{}' on non-internal path {}", jwt.getSubject(),
                        request.getRequestURI());
            }
            filterChain.doFilter(new VerifiedIdentityRequestWrapper(request, Map.of()), response);
            return;
        }

        String email = jwt.getSubject();
        String userId = jwt.getClaimAsString("userId");
        String roles = jwt.getClaimAsString("roles");

        List<SimpleGrantedAuthority> authorities = roles == null ? List.of() : Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(role -> !role.isEmpty())
                .filter(role -> !ServiceIdentity.AUTHORITY.equals(role))
                .map(SimpleGrantedAuthority::new)
                .toList();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(email, null, authorities);
        authentication.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        Map<String, String> identity = new HashMap<>();
        identity.put(IdentityHeaders.USER_ID, userId);
        identity.put(IdentityHeaders.USER_EMAIL, email);
        identity.put(IdentityHeaders.USER_ROLES, roles);

        filterChain.doFilter(new VerifiedIdentityRequestWrapper(request, identity), response);
    }

    private boolean isServiceToken(Jwt jwt) {
        List<String> audience = jwt.getAudience();
        return audience != null && audience.contains(internalAudience)
                && ServiceIdentity.TOKEN_USE_SERVICE.equals(jwt.getClaimAsString(ServiceIdentity.TOKEN_USE_CLAIM));
    }

    private void authenticateService(Jwt jwt) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                jwt.getSubject(), null, List.of(new SimpleGrantedAuthority(ServiceIdentity.AUTHORITY)));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    static String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = authorizationHeader.substring(7).trim();
        if (token.length() > 1 && token.startsWith("\"") && token.endsWith("\"")) {
            token = token.substring(1, token.length() - 1).trim();
        }
        return token.isEmpty() ? null : token;
    }
}
