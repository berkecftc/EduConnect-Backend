package com.educonnect.authservices.controller;

import com.educonnect.authservices.dto.request.EmailChangeRequest;
import com.educonnect.authservices.service.EmailChangeService;
import com.educonnect.common.web.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/email-change")
public class EmailChangeController {

    private final EmailChangeService emailChangeService;

    public EmailChangeController(EmailChangeService emailChangeService) {
        this.emailChangeService = emailChangeService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> request(@Valid @RequestBody EmailChangeRequest request, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Kimlik doğrulanamadı.");
        }
        EmailChangeService.Result result = emailChangeService.request(userDetails.getUsername(), request);
        String message = result == EmailChangeService.Result.CHANGED
                ? "E-posta adresiniz değiştirildi. Yeni adresinizle tekrar giriş yapın."
                : "Yeni adresinize bir doğrulama bağlantısı gönderildi. Bağlantıya tıklayınca değişiklik tamamlanır.";
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("status", result.name(), "message", message));
    }

    @GetMapping("/confirm")
    public ResponseEntity<Void> confirm(@RequestParam(value = "token", required = false) String token) {
        boolean changed = emailChangeService.confirm(token);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(emailChangeService.loginRedirectUrl(changed)))
                .build();
    }
}
