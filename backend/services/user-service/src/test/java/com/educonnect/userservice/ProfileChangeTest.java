package com.educonnect.userservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.userservice.dto.message.UserEmailChangedMessage;
import com.educonnect.userservice.listener.EmailChangeListener;
import com.educonnect.userservice.models.AcademicTitle;
import com.educonnect.userservice.models.Academician;
import com.educonnect.userservice.models.Student;
import com.educonnect.userservice.repository.AcademicianRepository;
import com.educonnect.userservice.repository.StudentRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UserIntegrationTest
class ProfileChangeTest {

    private final UUID student = UUID.randomUUID();
    private final UUID teacher = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AcademicianRepository academicianRepository;

    @Autowired
    private EmailChangeListener emailChangeListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void createProfiles() {
        Student s = new Student(student, "Ayşe", "Demir", "C" + student.toString().substring(0, 8));
        s.setEmail(student + "@test.educonnect.local");
        studentRepository.save(s);
        Academician a = new Academician(teacher, "Kemal", "Öz", null);
        a.setEmail(teacher + "@test.educonnect.local");
        a.setAcademicTitle(AcademicTitle.ASSISTANT_PROFESSOR);
        academicianRepository.save(a);
    }

    @Test
    void officialFieldsAreChangedOnlyThroughAnApprovedRequest() throws Exception {
        mockMvc.perform(json(put("/api/users/profile/{id}", student), TestTokens.student(student), "{\"firstName\":\"Ayşegül\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("OFFICIAL_FIELD_LOCKED"));
        mockMvc.perform(json(put("/api/users/profile/{id}", student), TestTokens.student(student),
                        "{\"firstName\":\"Ayşe\",\"lastName\":\"Demir\",\"bio\":\"Merhaba\"}"))
                .andExpect(status().isOk());

        submit(TestTokens.student(student), "{\"firstName\":\"Ayşe\",\"reason\":\"aynı\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NO_CHANGES"));
        submit(TestTokens.student(student), "{\"title\":\"Prof. Dr.\",\"reason\":\"terfi\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TITLE_NOT_ALLOWED"));
        submit(TestTokens.student(student), "{\"firstName\":\"Ayşegül\"}")
                .andExpect(status().isBadRequest());
        String id = JsonPath.read(submit(TestTokens.student(student),
                        "{\"firstName\":\"Ayşegül\",\"reason\":\"Mahkeme kararıyla ad değişikliği\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        submit(TestTokens.student(student), "{\"lastName\":\"Kara\",\"reason\":\"evlilik\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CHANGE_REQUEST_PENDING"));

        mockMvc.perform(as(get("/api/users/profile/{id}", student), TestTokens.student(student)))
                .andExpect(jsonPath("$.firstName").value("Ayşe"));
        mockMvc.perform(as(get("/api/users/admin/change-requests"), TestTokens.student(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/users/admin/change-requests"), TestTokens.admin(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')].currentName").value("Ayşe Demir"));
        mockMvc.perform(as(post("/api/users/admin/change-requests/{id}/approve", id), TestTokens.student(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/users/admin/change-requests/{id}/approve", id), TestTokens.admin(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(as(post("/api/users/admin/change-requests/{id}/approve", id), TestTokens.admin(admin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CHANGE_REQUEST_CLOSED"));

        mockMvc.perform(as(get("/api/users/profile/{id}", student), TestTokens.student(student)))
                .andExpect(jsonPath("$.firstName").value("Ayşegül"))
                .andExpect(jsonPath("$.bio").value("Merhaba"));
        mockMvc.perform(as(get("/api/users/profile/me/change-requests"), TestTokens.student(student)))
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test
    void aRejectedTitleChangeLeavesTheTitleAsItWas() throws Exception {
        submit(TestTokens.academician(teacher), "{\"title\":\"Rektör\",\"reason\":\"terfi\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TITLE"));
        String id = JsonPath.read(submit(TestTokens.academician(teacher), "{\"title\":\"doc. dr.\",\"reason\":\"Doçentlik ataması\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.academicTitle").value("ASSOCIATE_PROFESSOR"))
                .andExpect(jsonPath("$.titleLabel").value("Doç. Dr."))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(json(post("/api/users/admin/change-requests/{id}/reject", id), TestTokens.admin(admin), "{\"note\":\"Atama belgesi yok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("Atama belgesi yok"));
        assertThat(academicianRepository.findById(teacher).orElseThrow().getAcademicTitle()).isEqualTo(AcademicTitle.ASSISTANT_PROFESSOR);
        assertThat(jdbcTemplate.queryForList("select convert_from(body, 'UTF8') from user_db.outbox_messages "
                + "where routing_key = 'notification.request' and convert_from(body, 'UTF8') like ?", String.class, "%" + teacher + "%"))
                .singleElement().asString()
                .contains("\"category\":\"ACCOUNT\"", "PROFILE_CHANGE_REJECTED", "Atama belgesi yok");

        String approved = JsonPath.read(submit(TestTokens.academician(teacher), "{\"title\":\"Doç. Dr.\",\"reason\":\"Belge eklendi\"}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        mockMvc.perform(as(post("/api/users/admin/change-requests/{id}/approve", approved), TestTokens.admin(admin)))
                .andExpect(status().isOk());
        assertThat(academicianRepository.findById(teacher).orElseThrow().getTitle()).isEqualTo("Doç. Dr.");
    }

    @Test
    void aConfirmedEmailChangeReachesEveryProfileRow() throws Exception {
        mockMvc.perform(as(get("/api/users/profile/{id}", student), TestTokens.student(student)))
                .andExpect(jsonPath("$.email").value(student + "@test.educonnect.local"));
        emailChangeListener.handleEmailChange(new UserEmailChangedMessage(student, "yeni-" + student + "@test.educonnect.local"));
        assertThat(studentRepository.findById(student).orElseThrow().getEmail()).isEqualTo("yeni-" + student + "@test.educonnect.local");
        mockMvc.perform(as(get("/api/users/profile/{id}", student), TestTokens.student(student)))
                .andExpect(jsonPath("$.email").value("yeni-" + student + "@test.educonnect.local"));
    }

    private ResultActions submit(String token, String body) throws Exception {
        return mockMvc.perform(json(post("/api/users/profile/me/change-requests"), token, body));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
