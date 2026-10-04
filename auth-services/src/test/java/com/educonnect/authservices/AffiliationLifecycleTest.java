package com.educonnect.authservices;

import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.StaffStatus;
import com.educonnect.authservices.models.StudentStatus;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.authservices.service.AcademicianAssignmentGuard;
import com.educonnect.authservices.service.JWTService;
import com.educonnect.common.web.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AuthIntegrationTest
class AffiliationLifecycleTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AcademicianAssignmentGuard assignmentGuard;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = save(Role.ROLE_ADMIN);
    }

    @Test
    void aStudentGoesOnLeaveAndComesBackWithTheAccountOpen() throws Exception {
        User student = save(Role.ROLE_STUDENT);

        change(student, "student-status", "{\"status\":\"ON_LEAVE\"}", student)
                .andExpect(status().isForbidden());
        change(student, "student-status", "{\"status\":\"SLEEPING\"}", admin)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS"));
        change(student, "student-status", "{\"status\":\"on_leave\",\"effectiveDate\":\"2026-10-01\",\"reason\":\"Sağlık\"}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentStatus").value("ON_LEAVE"))
                .andExpect(jsonPath("$.history[0].previousStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.history[0].effectiveDate").value("2026-10-01"));
        change(student, "student-status", "{\"status\":\"ON_LEAVE\"}", admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STATUS_UNCHANGED"));
        change(student, "student-status", "{\"status\":\"ACTIVE\"}", admin).andExpect(status().isOk());

        User reloaded = userRepository.findById(student.getId()).orElseThrow();
        assertThat(reloaded.getRoles()).contains(Role.ROLE_STUDENT);
        assertThat(reloaded.getStudentStatus()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(published(student)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList("select convert_from(body, 'UTF8') from auth_db.outbox_messages "
                + "where routing_key = 'notification.request' and convert_from(body, 'UTF8') like ?", String.class, "%" + student.getId() + "%"))
                .hasSize(2)
                .anySatisfy(body -> assertThat(body).contains("AFFILIATION_STATUS", "1 Ekim 2026", "kayıt dondurma", "Sağlık"));
        mockMvc.perform(get("/api/auth/admin/users/{id}/affiliations", student.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$.history.length()").value(2));
    }

    @Test
    void graduationClosesASingleAffiliationAccount() throws Exception {
        User student = save(Role.ROLE_STUDENT);

        change(student, "student-status", "{\"status\":\"GRADUATED\"}", admin).andExpect(status().isOk());

        assertThat(userRepository.findById(student.getId())).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_db.outbox_messages where routing_key = 'user.delete' "
                + "and convert_from(body, 'UTF8') like ?", Integer.class, "%" + student.getId() + "%")).isEqualTo(1);
    }

    @Test
    void anEndedAffiliationKeepsTheOtherOneAndItsAccount() throws Exception {
        User dual = save(Role.ROLE_STUDENT, Role.ROLE_ACADEMICIAN);

        change(dual, "student-status", "{\"status\":\"GRADUATED\"}", admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentStatus").value("GRADUATED"))
                .andExpect(jsonPath("$.staffStatus").value("ACTIVE"));
        User reloaded = userRepository.findById(dual.getId()).orElseThrow();
        assertThat(reloaded.getRoles()).containsExactly(Role.ROLE_ACADEMICIAN);
        change(dual, "student-status", "{\"status\":\"ACTIVE\"}", admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_STUDENT_AFFILIATION"));

        doThrow(new ConflictException("ACADEMICIAN_HAS_COURSES", "Önce dersleri devredin."))
                .when(assignmentGuard).requireNoActiveAssignments(dual.getId());
        change(dual, "staff-status", "{\"status\":\"RETIRED\"}", admin)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ACADEMICIAN_HAS_COURSES"));
        User unchanged = userRepository.findById(dual.getId()).orElseThrow();
        assertThat(unchanged.getRoles()).contains(Role.ROLE_ACADEMICIAN);
        assertThat(unchanged.getStaffStatus()).isNotEqualTo(StaffStatus.RETIRED);
    }

    private ResultActions change(User target, String kind, String body, User actor) throws Exception {
        return mockMvc.perform(put("/api/auth/admin/users/{id}/" + kind, target.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(actor))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private int published(User user) {
        return jdbcTemplate.queryForObject("select count(*) from auth_db.outbox_messages where routing_key = 'user.affiliation.status' "
                + "and convert_from(body, 'UTF8') like ?", Integer.class, "%" + user.getId() + "%");
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private User save(Role... roles) {
        return userRepository.save(new User(roles[0].name().toLowerCase().replace("role_", "") + "-" + UUID.randomUUID()
                + "@test.educonnect.local", "{noop}unused", new HashSet<>(Set.of(roles))));
    }
}
