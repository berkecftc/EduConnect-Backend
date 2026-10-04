package com.educonnect.authservices;

import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.authservices.models.AccountStatus;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.service.JWTService;
import com.educonnect.common.test.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AuthIntegrationTest
class AuthAuthorizationTest {

    private static final String PASSWORD = "Pw-" + UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User student;
    private User academician;
    private User admin;

    @BeforeEach
    void createUsers() {
        student = save(Role.ROLE_STUDENT);
        academician = save(Role.ROLE_ACADEMICIAN);
        admin = save(Role.ROLE_ADMIN);
    }

    @Test
    void adminEndpointsRejectStudentsAndAcademicians() throws Exception {
        for (String path : new String[]{"/api/auth/admin/users", "/api/auth/admin/requests/students",
                "/api/auth/admin/requests/academicians", "/api/auth/admin/audit-log"}) {
            mockMvc.perform(as(get(path), student))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
            mockMvc.perform(as(get(path), academician))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
            mockMvc.perform(as(get(path), admin))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void rolesComeFromTheDatabaseNotFromTokenClaims() throws Exception {
        String forgedClaims = jwtService.generateToken(Map.of("userId", student.getId().toString(), "roles", "ROLE_ADMIN"), student);
        mockMvc.perform(get("/api/auth/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedClaims))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokensSignedByAnotherKeyAreRejected() throws Exception {
        mockMvc.perform(get("/api/auth/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.admin(admin.getId()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminsSuspendAndReactivateAccounts() throws Exception {
        mockMvc.perform(as(put("/api/auth/admin/users/{id}/suspend", academician.getId()), student)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"deneme\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(put("/api/auth/admin/users/{id}/suspend", student.getId()), academician)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"deneme\"}"))
                .andExpect(status().isForbidden());
        assertThat(statusOf(academician)).isEqualTo(AccountStatus.ACTIVE);
        assertThat(statusOf(student)).isEqualTo(AccountStatus.ACTIVE);

        mockMvc.perform(as(put("/api/auth/admin/users/{id}/suspend", student.getId()), admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"kural ihlali\"}"))
                .andExpect(status().isOk());
        assertThat(statusOf(student)).isEqualTo(AccountStatus.SUSPENDED);

        mockMvc.perform(as(put("/api/auth/admin/users/{id}/reactivate", student.getId()), academician))
                .andExpect(status().isForbidden());
        assertThat(statusOf(student)).isEqualTo(AccountStatus.SUSPENDED);

        mockMvc.perform(as(put("/api/auth/admin/users/{id}/reactivate", student.getId()), admin))
                .andExpect(status().isOk());
        assertThat(statusOf(student)).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void adminAccountsCannotBeSuspended() throws Exception {
        User otherAdmin = save(Role.ROLE_ADMIN);
        mockMvc.perform(as(put("/api/auth/admin/users/{id}/suspend", otherAdmin.getId()), admin))
                .andExpect(status().isConflict());
        mockMvc.perform(as(put("/api/auth/admin/users/{id}/suspend", admin.getId()), admin))
                .andExpect(status().isBadRequest());
        assertThat(statusOf(otherAdmin)).isEqualTo(AccountStatus.ACTIVE);
        assertThat(statusOf(admin)).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void onlyAdminsDeleteUsers() throws Exception {
        mockMvc.perform(as(delete("/api/auth/admin/users/{id}", academician.getId()), student))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(delete("/api/auth/admin/users/{id}", student.getId()), academician))
                .andExpect(status().isForbidden());
        assertThat(userRepository.existsById(student.getId())).isTrue();
        assertThat(userRepository.existsById(academician.getId())).isTrue();

        mockMvc.perform(as(delete("/api/auth/admin/users/{id}", student.getId()), admin))
                .andExpect(status().isOk());
        assertThat(userRepository.existsById(student.getId())).isFalse();
    }

    @Test
    void deletingAnAcademicianIsBlockedWhenAssignmentsCannotBeVerified() throws Exception {
        mockMvc.perform(as(delete("/api/auth/admin/users/{id}", academician.getId()), admin))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));
        assertThat(userRepository.existsById(academician.getId())).isTrue();
    }

    @Test
    void deletingAnAcademicianAnswers503WhenAssignmentsCannotBeVerified() throws Exception {
        mockMvc.perform(as(delete("/api/auth/admin/users/{id}", academician.getId()), admin))
                .andExpect(status().isServiceUnavailable());
        assertThat(userRepository.existsById(academician.getId())).isTrue();
    }

    @Test
    void theLastAdminCannotBeDeleted() throws Exception {
        userRepository.findAllByRolesContaining(Role.ROLE_ADMIN).stream()
                .filter(other -> !other.getId().equals(admin.getId()))
                .forEach(userRepository::delete);

        mockMvc.perform(as(delete("/api/auth/admin/users/{id}", admin.getId()), admin))
                .andExpect(status().isConflict());
        assertThat(userRepository.existsById(admin.getId())).isTrue();
    }

    @Test
    void closedAdminEndpointsStayBehindTheAdminRole() throws Exception {
        mockMvc.perform(as(post("/api/auth/admin/promote/{id}", student.getId()), student))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/auth/admin/promote/{id}", student.getId()), admin))
                .andExpect(status().isGone());
    }

    @Test
    void changePasswordRequiresAValidTokenAndTheCurrentPassword() throws Exception {
        User owner = saveWithPassword(Role.ROLE_STUDENT);
        String wrongCurrent = "{\"currentPassword\":\"yanlis-parola-1\",\"newPassword\":\"Yeni-Parola-2026\",\"confirmationPassword\":\"Yeni-Parola-2026\"}";
        String valid = "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"Yeni-Parola-2026\",\"confirmationPassword\":\"Yeni-Parola-2026\"}";

        mockMvc.perform(post("/api/auth/change-password").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/change-password")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.student(owner.getId())))
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(post("/api/auth/change-password"), owner)
                        .contentType(MediaType.APPLICATION_JSON).content(wrongCurrent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("WRONG_CURRENT_PASSWORD"));
        assertThat(passwordEncoder.matches(PASSWORD, userRepository.findById(owner.getId()).orElseThrow().getPassword())).isTrue();

        mockMvc.perform(as(post("/api/auth/change-password"), owner)
                        .contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isOk());
        assertThat(passwordEncoder.matches("Yeni-Parola-2026", userRepository.findById(owner.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test
    void aTokenOfADeletedUserIsRejected() throws Exception {
        String token = jwtService.generateToken(admin);
        User secondAdmin = save(Role.ROLE_ADMIN);
        userRepository.delete(admin);

        mockMvc.perform(get("/api/auth/admin/users").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(get("/api/auth/admin/users"), secondAdmin))
                .andExpect(status().isOk());
    }

    @Test
    void internalEndpointsAcceptOnlyServiceTokens() throws Exception {
        String body = "[\"" + student.getId() + "\"]";

        mockMvc.perform(post("/api/auth/internal/users/emails").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(post("/api/auth/internal/users/emails"), admin)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/internal/users/emails")
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.service("club-service")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/internal/users/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateServiceToken("club-service"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(student.getEmail()));

        academician.suspend("Test", Instant.now());
        userRepository.save(academician);
        String pair = "[\"" + student.getId() + "\",\"" + academician.getId() + "\"]";
        mockMvc.perform(post("/api/auth/internal/users/contacts").contentType(MediaType.APPLICATION_JSON).content(pair))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/internal/users/contacts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateServiceToken("notification-service"))
                        .contentType(MediaType.APPLICATION_JSON).content(pair))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + student.getId() + "')].email").value(student.getEmail()))
                .andExpect(jsonPath("$[?(@.id == '" + student.getId() + "')].active").value(true))
                .andExpect(jsonPath("$[?(@.id == '" + academician.getId() + "')].active").value(false));
    }

    @Test
    void aServiceTokenGrantsNothingOutsideInternalPaths() throws Exception {
        mockMvc.perform(get("/api/auth/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateServiceToken("club-service")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theServiceTokenEndpointRequiresValidClientCredentials() throws Exception {
        mockMvc.perform(tokenRequest("client_credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
        mockMvc.perform(tokenRequest("client_credentials")
                        .header(HttpHeaders.AUTHORIZATION, basic(AuthTestProperties.SERVICE_CLIENT_ID, "yanlis")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(tokenRequest("client_credentials")
                        .header(HttpHeaders.AUTHORIZATION, basic("bilinmeyen", AuthTestProperties.SERVICE_CLIENT_SECRET)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(tokenRequest("password")
                        .header(HttpHeaders.AUTHORIZATION, basic(AuthTestProperties.SERVICE_CLIENT_ID, AuthTestProperties.SERVICE_CLIENT_SECRET)))
                .andExpect(status().isBadRequest());

        String response = mockMvc.perform(tokenRequest("client_credentials")
                        .header(HttpHeaders.AUTHORIZATION, basic(AuthTestProperties.SERVICE_CLIENT_ID, AuthTestProperties.SERVICE_CLIENT_SECRET)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String accessToken = JsonPath.read(response, "$.access_token");

        mockMvc.perform(post("/api/auth/internal/users/emails")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content("[\"" + student.getId() + "\"]"))
                .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User user) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(user));
    }

    private static MockHttpServletRequestBuilder tokenRequest(String grantType) {
        return post("/api/auth/internal/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", grantType);
    }

    private static String basic(String clientId, String secret) {
        return "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
    }

    private AccountStatus statusOf(User user) {
        return userRepository.findById(user.getId()).orElseThrow().getStatus();
    }

    private User save(Role role) {
        return userRepository.save(new User(email(role), "{noop}unused", Set.of(role)));
    }

    private User saveWithPassword(Role role) {
        return userRepository.save(new User(email(role), passwordEncoder.encode(PASSWORD), Set.of(role)));
    }

    private static String email(Role role) {
        return role.name().toLowerCase().replace("role_", "") + "-" + UUID.randomUUID() + "@test.educonnect.local";
    }
}
