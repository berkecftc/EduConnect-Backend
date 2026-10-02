package com.educonnect.authservices.service;

import com.educonnect.authservices.config.AuthSecurityProperties;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class InstitutionPolicy {

    private static final Logger log = LoggerFactory.getLogger(InstitutionPolicy.class);
    private static final String PROFILE_BY_STUDENT_NUMBER = "http://user-service/api/users/internal/profiles/by-student-number/{number}";

    private final AuthSecurityProperties.Institution institution;
    private final Pattern studentNumberPattern;
    private final UserRepository userRepository;
    private final StudentRequestRepository studentRequestRepository;
    private final RestClient restClient;
    private final JWTService jwtService;

    public InstitutionPolicy(AuthSecurityProperties properties,
                             UserRepository userRepository,
                             StudentRequestRepository studentRequestRepository,
                             @LoadBalanced RestClient.Builder restClientBuilder,
                             JWTService jwtService) {
        this.institution = properties.institution();
        this.studentNumberPattern = Pattern.compile(institution.studentNumberPattern());
        this.userRepository = userRepository;
        this.studentRequestRepository = studentRequestRepository;
        this.restClient = restClientBuilder.build();
        this.jwtService = jwtService;
    }

    public void requireStudentEmail(String email) {
        requireDomain(email, institution.studentEmailDomains(), "öğrenci");
    }

    public void requireStaffEmail(String email) {
        requireDomain(email, institution.staffEmailDomains(), "personel");
    }

    public String requireStudentNumber(String raw) {
        String number = raw == null ? "" : raw.strip();
        if (number.isEmpty()) {
            throw new BadRequestException("STUDENT_NUMBER_REQUIRED", "Öğrenci numarası zorunludur.");
        }
        if (!studentNumberPattern.matcher(number).matches()) {
            throw new BadRequestException("INVALID_STUDENT_NUMBER", "Öğrenci numarası kurumun biçimine uymuyor.");
        }
        return number;
    }

    public void requireStudentNumberAvailable(String number, Long ignoredRequestId) {
        boolean pending = studentRequestRepository.findFirstByStudentNumber(number)
                .filter(request -> !request.getId().equals(ignoredRequestId))
                .isPresent();
        if (pending || userRepository.existsByStudentNumber(number) || profileExists(number)) {
            throw new ConflictException("STUDENT_NUMBER_TAKEN", "Bu öğrenci numarasıyla kayıtlı bir hesap veya başvuru var.");
        }
    }

    private void requireDomain(String email, List<String> domains, String kind) {
        if (domains.isEmpty()) {
            return;
        }
        String domain = email == null ? "" : email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        if (!domains.contains(domain)) {
            throw new BadRequestException("EMAIL_DOMAIN_NOT_ALLOWED",
                    "Kurumsal " + kind + " e-posta adresi gerekli (" + String.join(", ", domains.stream().map(d -> "@" + d).toList()) + ").");
        }
    }

    private boolean profileExists(String number) {
        try {
            restClient.get()
                    .uri(PROFILE_BY_STUDENT_NUMBER, number)
                    .headers(headers -> headers.setBearerAuth(jwtService.generateServiceToken(AcademicianAssignmentGuard.SERVICE_CLIENT_ID)))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (RestClientException | IllegalArgumentException | IllegalStateException e) {
            log.warn("Öğrenci numarası doğrulanamadı: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Öğrenci numarası şu an doğrulanamıyor. Biraz sonra tekrar deneyin.");
        }
    }
}
