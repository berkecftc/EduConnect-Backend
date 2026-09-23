package com.educonnect.authservices.controller;

import com.educonnect.authservices.service.AuthServiceImpl;
import com.educonnect.authservices.service.JWTService;
import com.educonnect.authservices.service.ServiceClientAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth/internal")
public class InternalAuthController {

    private static final Logger log = LoggerFactory.getLogger(InternalAuthController.class);

    private final JWTService jwtService;
    private final ServiceClientAuthenticator serviceClientAuthenticator;
    private final AuthServiceImpl authService;

    public InternalAuthController(JWTService jwtService,
                                  ServiceClientAuthenticator serviceClientAuthenticator,
                                  AuthServiceImpl authService) {
        this.jwtService = jwtService;
        this.serviceClientAuthenticator = serviceClientAuthenticator;
        this.authService = authService;
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> issueServiceToken(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestParam(value = "grant_type", required = false) String grantType) {
        if (!"client_credentials".equals(grantType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "unsupported_grant_type"));
        }

        String[] credentials = decodeBasicCredentials(authorization);
        if (credentials == null || !serviceClientAuthenticator.authenticate(credentials[0], credentials[1])) {
            log.warn("Rejected service token request for client '{}'", credentials == null ? null : credentials[0]);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"educonnect-internal\"")
                    .body(Map.of("error", "invalid_client"));
        }

        String clientId = credentials[0];
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "access_token", jwtService.generateServiceToken(clientId),
                        "token_type", "Bearer",
                        "expires_in", jwtService.getServiceTokenTtl().toSeconds()));
    }

    @PostMapping("/users/emails")
    public ResponseEntity<List<String>> getEmailsByIds(@RequestBody List<UUID> userIds) {
        return ResponseEntity.ok(authService.getEmailsByUserIds(userIds));
    }

    private static String[] decodeBasicCredentials(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
            return null;
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(authorization.substring(6).trim()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        int separator = decoded.indexOf(':');
        if (separator <= 0) {
            return null;
        }
        return new String[]{decoded.substring(0, separator), decoded.substring(separator + 1)};
    }
}
