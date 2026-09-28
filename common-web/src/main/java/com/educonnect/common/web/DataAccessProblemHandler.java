package com.educonnect.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(0)
public class DataAccessProblemHandler {

    private static final Logger log = LoggerFactory.getLogger(DataAccessProblemHandler.class);

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleConcurrentUpdate(OptimisticLockingFailureException ex,
                                                                HttpServletRequest request) {
        log.warn("Concurrent update conflict on {}: {}", request.getRequestURI(), ex.getMessage());
        return Problems.response(Problems.create(HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "Kayıt başka bir işlem tarafından değiştirildi. Sayfayı yenileyip tekrar deneyin.",
                request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return Problems.response(Problems.create(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "Bu işlem mevcut bir kayıtla çakışıyor. Sayfayı yenileyip tekrar deneyin.",
                request.getRequestURI()));
    }
}
