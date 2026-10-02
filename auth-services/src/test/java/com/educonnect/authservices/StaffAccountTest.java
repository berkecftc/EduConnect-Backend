package com.educonnect.authservices;

import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.StaffPermission;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.authservices.service.InstitutionPolicy;
import com.educonnect.authservices.service.JWTService;
import com.educonnect.common.web.BadRequestException;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AuthIntegrationTest
class StaffAccountTest {

    private static final String PASSWORD = "Pw-" + UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private InstitutionPolicy institutionPolicy;

    private User admin;
    private final UUID faculty = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        admin = userRepository.save(new User("admin-" + UUID.randomUUID() + "@test.educonnect.local", "{noop}x",
                new HashSet<>(Set.of(Role.ROLE_ADMIN))));
    }

    @Test
    void adminsOpenStaffAccountsWithScopedPermissions() throws Exception {
        String email = "ogrenciisleri-" + UUID.randomUUID() + "@test.educonnect.local";
        String body = "{\"email\":\"" + email + "\",\"displayName\":\"Öğrenci İşleri\",\"grants\":["
                + "{\"permission\":\"STUDENT_VERIFIER\",\"facultyIds\":[\"" + faculty + "\"]},{\"permission\":\"account_manager\"}]}";

        User student = userRepository.save(new User("s-" + UUID.randomUUID() + "@test.educonnect.local", "{noop}x",
                new HashSet<>(Set.of(Role.ROLE_STUDENT))));
        create(body, student).andExpect(status().isForbidden());
        String id = JsonPath.read(create(body, admin)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.displayName").value("Öğrenci İşleri"))
                .andExpect(jsonPath("$.grants.length()").value(2))
                .andExpect(jsonPath("$.grants[?(@.permission == 'STUDENT_VERIFIER')].facultyId").value(faculty.toString()))
                .andReturn().getResponse().getContentAsString(), "$.id");
        create(body, admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_TAKEN"));

        User staff = userRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(staff.getRoles()).containsExactly(Role.ROLE_STAFF);
        assertThat(staff.permissionAuthorities()).containsExactly("PERM_ACCOUNT_MANAGER", "PERM_STUDENT_VERIFIER");
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_db.outbox_messages where routing_key = 'user.password.reset' "
                + "and convert_from(body, 'UTF8') like ?", Integer.class, "%" + email + "%ACCOUNT_SETUP%")).isEqualTo(1);

        staff.setPassword(passwordEncoder.encode(PASSWORD));
        userRepository.save(staff);
        String token = JsonPath.read(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryRole").value("ROLE_STAFF"))
                .andReturn().getResponse().getContentAsString(), "$.token");
        String claims = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"roles\":\"ROLE_STAFF,PERM_ACCOUNT_MANAGER,PERM_STUDENT_VERIFIER\"");

        mockMvc.perform(get("/api/auth/internal/staff/{id}/grants", id)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateServiceToken(AuthTestProperties.SERVICE_CLIENT_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/auth/internal/staff/{id}/grants", id).header(HttpHeaders.AUTHORIZATION, bearer(staff)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/auth/admin/staff-accounts/{id}/permissions", id)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grants\":[{\"permission\":\"MODERATOR\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grants[0].permission").value("MODERATOR"));
        assertThat(userRepository.findById(staff.getId()).orElseThrow().permissionAuthorities()).containsExactly("PERM_MODERATOR");
        mockMvc.perform(get("/api/auth/admin/staff-accounts").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].grants[0].permission").value(StaffPermission.MODERATOR.name()));
    }

    @Test
    void grantsAreValidatedAndOnlyStaffAccountsHoldThem() throws Exception {
        create("{\"email\":\"x-" + UUID.randomUUID() + "@test.educonnect.local\",\"displayName\":\"X\",\"grants\":[{\"permission\":\"MODERATOR\",\"facultyIds\":[\"" + faculty + "\"]}]}", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_NOT_ALLOWED"));
        create("{\"email\":\"x-" + UUID.randomUUID() + "@test.educonnect.local\",\"displayName\":\"X\",\"grants\":[{\"permission\":\"ROOT\"}]}", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PERMISSION"));
        create("{\"email\":\"x-" + UUID.randomUUID() + "@test.educonnect.local\",\"displayName\":\"X\",\"grants\":[]}", admin)
                .andExpect(status().isBadRequest());
        doThrow(new BadRequestException("FACULTY_NOT_FOUND", "Fakülte bulunamadı.")).when(institutionPolicy).requireFaculty(faculty);
        create("{\"email\":\"x-" + UUID.randomUUID() + "@test.educonnect.local\",\"displayName\":\"X\",\"grants\":[{\"permission\":\"STUDENT_VERIFIER\",\"facultyIds\":[\"" + faculty + "\"]}]}", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FACULTY_NOT_FOUND"));

        User academician = userRepository.save(new User("a-" + UUID.randomUUID() + "@test.educonnect.local", "{noop}x",
                new HashSet<>(Set.of(Role.ROLE_ACADEMICIAN))));
        mockMvc.perform(put("/api/auth/admin/staff-accounts/{id}/permissions", academician.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grants\":[{\"permission\":\"MODERATOR\"}]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOT_A_STAFF_ACCOUNT"));
    }

    private ResultActions create(String body, User actor) throws Exception {
        return mockMvc.perform(post("/api/auth/admin/staff-accounts")
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
