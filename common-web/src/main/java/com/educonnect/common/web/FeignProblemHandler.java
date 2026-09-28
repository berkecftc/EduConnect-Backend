package com.educonnect.common.web;

import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(0)
public class FeignProblemHandler {

    private static final Logger log = LoggerFactory.getLogger(FeignProblemHandler.class);

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ProblemDetail> handleFeign(FeignException ex, HttpServletRequest request) {
        if (ex.status() < 0 || ex.status() >= 500) {
            log.warn("Upstream call failed on {} {}: status={} {}", request.getMethod(), request.getRequestURI(),
                    ex.status(), ex.getMessage());
            return Problems.response(Problems.create(HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE", null,
                    request.getRequestURI()));
        }
        log.error("Upstream call rejected on {} {}: status={}", request.getMethod(), request.getRequestURI(),
                ex.status(), ex);
        return Problems.response(Problems.create(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", null,
                request.getRequestURI()));
    }
}
