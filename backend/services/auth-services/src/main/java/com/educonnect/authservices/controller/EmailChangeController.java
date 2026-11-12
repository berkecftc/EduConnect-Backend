package com.educonnect.authservices.controller;

import com.educonnect.common.web.ForbiddenException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/email-change")
public class EmailChangeController {

    @PostMapping({"", "/confirm"})
    public ResponseEntity<Void> notAllowed() {
        throw new ForbiddenException("EMAIL_CHANGE_NOT_ALLOWED",
                "Giriş e-postası kullanıcı tarafından değiştirilemez. Değişiklik için öğrenci işleri veya hesap yöneticisine başvurun.");
    }
}
