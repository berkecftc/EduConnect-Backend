package com.educonnect.assignmentservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ConcurrentUpdateExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ConcurrentUpdateExceptionHandler.class);

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        log.warn("Eşzamanlı güncelleme çakışması: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", HttpStatus.CONFLICT.value());
        body.put("error", HttpStatus.CONFLICT.getReasonPhrase());
        body.put("message", "Kayıt başka bir işlem tarafından değiştirildi. Sayfayı yenileyip tekrar deneyin.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
