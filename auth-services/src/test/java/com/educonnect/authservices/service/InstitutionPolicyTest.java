package com.educonnect.authservices.service;

import com.educonnect.authservices.config.AuthSecurityProperties;
import com.educonnect.authservices.models.StudentRegistrationRequest;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InstitutionPolicyTest {

    private static final String PROFILE = "http://user-service/api/users/internal/profiles/by-student-number/";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final StudentRequestRepository requestRepository = mock(StudentRequestRepository.class);
    private MockRestServiceServer server;

    private InstitutionPolicy policy(List<String> studentDomains, List<String> staffDomains, String pattern) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        JWTService jwtService = mock(JWTService.class);
        when(jwtService.generateServiceToken(anyString())).thenReturn("service-token");
        AuthSecurityProperties properties = new AuthSecurityProperties(null, null, null, null, null, null,
                new AuthSecurityProperties.Institution(studentDomains, staffDomains, pattern));
        return new InstitutionPolicy(properties, userRepository, requestRepository, builder, jwtService);
    }

    @Test
    void institutionalDomainsAreRequiredOnlyWhenConfigured() {
        InstitutionPolicy open = policy(null, null, null);
        assertThatCode(() -> open.requireStudentEmail("ali@gmail.com")).doesNotThrowAnyException();

        InstitutionPolicy policy = policy(List.of("@OGR.uni.edu.tr"), List.of("uni.edu.tr"), null);
        assertThatCode(() -> policy.requireStudentEmail("ali@ogr.uni.edu.tr")).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireStaffEmail("hoca@UNI.EDU.TR")).doesNotThrowAnyException();
        assertCode(() -> policy.requireStudentEmail("ali@uni.edu.tr"), "EMAIL_DOMAIN_NOT_ALLOWED");
        assertCode(() -> policy.requireStaffEmail("hoca@ogr.uni.edu.tr"), "EMAIL_DOMAIN_NOT_ALLOWED");
        assertCode(() -> policy.requireStudentEmail("ali@kotu-ogr.uni.edu.tr"), "EMAIL_DOMAIN_NOT_ALLOWED");
    }

    @Test
    void studentNumbersFollowTheInstitutionFormat() {
        InstitutionPolicy policy = policy(null, null, "20[0-9]{8}");

        assertThat(policy.requireStudentNumber(" 2026000123 ")).isEqualTo("2026000123");
        assertCode(() -> policy.requireStudentNumber(null), "STUDENT_NUMBER_REQUIRED");
        assertCode(() -> policy.requireStudentNumber("123"), "INVALID_STUDENT_NUMBER");
        assertThat(policy(null, null, null).requireStudentNumber("12345")).isEqualTo("12345");
    }

    @Test
    void aStudentNumberIsTakenByAPendingRequestAnAccountOrAnExistingProfile() {
        InstitutionPolicy policy = policy(null, null, null);
        server.expect(requestTo(PROFILE + "11111")).andRespond(withResourceNotFound());
        server.expect(requestTo(PROFILE + "33333"))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andRespond(withSuccess());
        server.expect(requestTo(PROFILE + "44444")).andRespond(withServerError());
        StudentRegistrationRequest pending = new StudentRegistrationRequest();
        pending.setId(7L);
        when(requestRepository.findFirstByStudentNumber("11111")).thenReturn(Optional.of(pending));
        assertCode(() -> policy.requireStudentNumberAvailable("11111", null), "STUDENT_NUMBER_TAKEN");
        assertThatCode(() -> policy.requireStudentNumberAvailable("11111", 7L)).doesNotThrowAnyException();

        when(userRepository.existsByStudentNumber("22222")).thenReturn(true);
        assertCode(() -> policy.requireStudentNumberAvailable("22222", null), "STUDENT_NUMBER_TAKEN");
        assertCode(() -> policy.requireStudentNumberAvailable("33333", null), "STUDENT_NUMBER_TAKEN");
        assertThatThrownBy(() -> policy.requireStudentNumberAvailable("44444", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        server.verify();
    }

    private static void assertCode(Runnable call, String code) {
        assertThatThrownBy(call::run).isInstanceOf(ApiException.class).hasFieldOrPropertyWithValue("errorCode", code);
    }
}
