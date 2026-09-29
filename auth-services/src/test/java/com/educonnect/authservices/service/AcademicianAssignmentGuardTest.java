package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AcademicianAssignmentGuardTest {

    private static final String COURSES = "http://course-service/api/courses/internal/instructors/%s/course-ids";
    private static final String CLUBS = "http://club-service/api/clubs/internal/by-advisor/%s/ids";

    private final UUID userId = UUID.randomUUID();

    private MockRestServiceServer server;
    private UserRepository userRepository;
    private AcademicianAssignmentGuard guard;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        JWTService jwtService = mock(JWTService.class);
        when(jwtService.generateServiceToken(anyString())).thenReturn("service-token");
        userRepository = mock(UserRepository.class);
        guard = new AcademicianAssignmentGuard(builder, jwtService, userRepository);
    }

    @Test
    void nonAcademicianIsNotChecked() {
        givenUserWithRole(Role.ROLE_STUDENT);

        assertThatCode(() -> guard.requireNoActiveAssignments(userId)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void academicianWithoutCoursesOrClubsCanBeDeleted() {
        givenUserWithRole(Role.ROLE_ACADEMICIAN);
        server.expect(requestTo(String.format(COURSES, userId)))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(String.format(CLUBS, userId)))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatCode(() -> guard.requireNoActiveAssignments(userId)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void academicianWithCoursesIsBlocked() {
        givenUserWithRole(Role.ROLE_ACADEMICIAN);
        server.expect(requestTo(String.format(COURSES, userId)))
                .andRespond(withSuccess("[\"" + UUID.randomUUID() + "\"]", MediaType.APPLICATION_JSON));
        server.expect(requestTo(String.format(CLUBS, userId)))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> guard.requireNoActiveAssignments(userId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void unreachableServiceFailsClosed() {
        givenUserWithRole(Role.ROLE_ACADEMICIAN);
        server.expect(requestTo(String.format(COURSES, userId))).andRespond(withServerError());

        assertThatThrownBy(() -> guard.requireNoActiveAssignments(userId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private void givenUserWithRole(Role role) {
        User user = new User();
        user.setId(userId);
        user.setRoles(Set.of(role));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }
}
