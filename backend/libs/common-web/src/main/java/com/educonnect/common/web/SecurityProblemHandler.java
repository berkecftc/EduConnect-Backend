package com.educonnect.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(0)
public class SecurityProblemHandler {

    static final String ACCESS_DENIED_MESSAGE = "Bu işlem için yetkiniz yok.";
    static final String BAD_CREDENTIALS_MESSAGE = "E-posta veya parola hatalı.";

    private static final AuthenticationTrustResolver TRUST_RESOLVER = new AuthenticationTrustResolverImpl();

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || TRUST_RESOLVER.isAnonymous(authentication)) {
            return Problems.response(Problems.create(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", null,
                    request.getRequestURI()));
        }
        return Problems.response(Problems.create(HttpStatus.FORBIDDEN, "ACCESS_DENIED", ACCESS_DENIED_MESSAGE,
                request.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        boolean badCredentials = ex instanceof BadCredentialsException || ex instanceof UsernameNotFoundException;
        return Problems.response(Problems.create(HttpStatus.UNAUTHORIZED,
                badCredentials ? "BAD_CREDENTIALS" : "AUTHENTICATION_FAILED",
                badCredentials ? BAD_CREDENTIALS_MESSAGE : Problems.defaultMessage(401),
                request.getRequestURI()));
    }
}
