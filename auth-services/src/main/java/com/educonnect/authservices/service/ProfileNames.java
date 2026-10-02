package com.educonnect.authservices.service;

import com.educonnect.common.web.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
public class ProfileNames {

    private static final Logger log = LoggerFactory.getLogger(ProfileNames.class);
    private static final String PROFILE = "http://user-service/api/users/internal/profiles/{id}";
    private static final ParameterizedTypeReference<Map<String, Object>> BODY = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final JWTService jwtService;

    public ProfileNames(@LoadBalanced RestClient.Builder restClientBuilder, JWTService jwtService) {
        this.restClient = restClientBuilder.build();
        this.jwtService = jwtService;
    }

    public Names of(UUID userId) {
        Map<String, Object> profile;
        try {
            profile = restClient.get()
                    .uri(PROFILE, userId)
                    .headers(headers -> headers.setBearerAuth(jwtService.generateServiceToken(AcademicianAssignmentGuard.SERVICE_CLIENT_ID)))
                    .retrieve()
                    .body(BODY);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ConflictException("PROFILE_NOT_FOUND", "Hesabın profili bulunamadı; bağlılık başvurusu yapılamıyor.");
        } catch (RestClientException | IllegalArgumentException | IllegalStateException e) {
            log.warn("Profil okunamadı: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Profil bilgisi şu an alınamıyor. Biraz sonra tekrar deneyin.");
        }
        if (profile == null) {
            throw new ConflictException("PROFILE_NOT_FOUND", "Hesabın profili bulunamadı; bağlılık başvurusu yapılamıyor.");
        }
        return new Names((String) profile.get("firstName"), (String) profile.get("lastName"));
    }

    public record Names(String firstName, String lastName) {
    }
}
