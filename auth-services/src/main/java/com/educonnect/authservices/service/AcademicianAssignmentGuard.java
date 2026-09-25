package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.models.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class AcademicianAssignmentGuard {

    private static final Logger log = LoggerFactory.getLogger(AcademicianAssignmentGuard.class);

    static final String SERVICE_CLIENT_ID = "auth-services";
    private static final String ADVISED_CLUBS_URI = "http://club-service/api/clubs/internal/by-advisor/{id}/ids";
    private static final String TAUGHT_COURSES_URI = "http://course-service/api/courses/internal/instructors/{id}/course-ids";
    private static final ParameterizedTypeReference<List<UUID>> ID_LIST = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;
    private final JWTService jwtService;
    private final UserRepository userRepository;

    public AcademicianAssignmentGuard(@LoadBalanced RestClient.Builder restClientBuilder,
                                      JWTService jwtService,
                                      UserRepository userRepository) {
        this.restClient = restClientBuilder.build();
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    public void requireNoActiveAssignments(UUID userId) {
        boolean academician = userRepository.findById(userId)
                .map(user -> user.getRoles() != null && user.getRoles().contains(Role.ROLE_ACADEMICIAN))
                .orElse(false);
        if (!academician) {
            return;
        }
        int courses = fetchIds(TAUGHT_COURSES_URI, userId).size();
        int clubs = fetchIds(ADVISED_CLUBS_URI, userId).size();
        if (courses > 0 || clubs > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, String.format(
                    "Akademisyen silinemez: %d dersin hocası ve %d kulübün danışmanı. Önce dersleri ve kulüpleri devredin.",
                    courses, clubs));
        }
    }

    private List<UUID> fetchIds(String uri, UUID userId) {
        try {
            List<UUID> ids = restClient.get()
                    .uri(uri, userId)
                    .headers(headers -> headers.setBearerAuth(jwtService.generateServiceToken(SERVICE_CLIENT_ID)))
                    .retrieve()
                    .body(ID_LIST);
            return ids != null ? ids : List.of();
        } catch (RestClientException e) {
            log.warn("Akademisyen görevleri doğrulanamadı: uri={}, reason={}", uri, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Akademisyenin ders ve kulüp görevleri doğrulanamadı. Biraz sonra tekrar deneyin.");
        }
    }
}
