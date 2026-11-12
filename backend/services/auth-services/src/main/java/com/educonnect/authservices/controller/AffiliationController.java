package com.educonnect.authservices.controller;

import com.educonnect.authservices.dto.request.AcademicianAffiliationRequest;
import com.educonnect.authservices.dto.request.StudentAffiliationRequest;
import com.educonnect.authservices.service.AffiliationService;
import com.educonnect.common.web.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth/affiliations")
public class AffiliationController {

    private final AffiliationService affiliationService;

    public AffiliationController(AffiliationService affiliationService) {
        this.affiliationService = affiliationService;
    }

    @PostMapping(value = "/student", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> requestStudent(@Valid @RequestPart("request") StudentAffiliationRequest request,
                                                 @RequestPart(value = "studentDocument", required = false) MultipartFile studentDocument,
                                                 Authentication authentication) {
        affiliationService.requestStudentAffiliation(email(authentication), request, studentDocument);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Öğrenci kaydı başvurunuz alındı; onay bekleniyor.");
    }

    @PostMapping(value = "/academician", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> requestAcademician(@Valid @RequestPart("request") AcademicianAffiliationRequest request,
                                                     @RequestPart(value = "idCardImage", required = false) MultipartFile idCardImage,
                                                     Authentication authentication) {
        affiliationService.requestAcademicianAffiliation(email(authentication), request, idCardImage);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body("Personel kaydı başvurunuz alındı; onay bekleniyor.");
    }

    private static String email(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Kimlik doğrulanamadı.");
        }
        return userDetails.getUsername();
    }
}
