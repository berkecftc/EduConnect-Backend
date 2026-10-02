package com.educonnect.userservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.userservice.dto.message.AcademicianProfileMessage;
import com.educonnect.userservice.dto.message.UserDeletedMessage;
import com.educonnect.userservice.dto.message.UserRegisteredMessage;
import com.educonnect.userservice.listener.ProfileCreationListener;
import com.educonnect.userservice.listener.UserDeletionListener;
import com.educonnect.userservice.models.AcademicTitle;
import com.educonnect.userservice.models.StaffCategory;
import com.educonnect.userservice.repository.AcademicianRepository;
import com.educonnect.userservice.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UserIntegrationTest
class AffiliationProfileTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProfileCreationListener creationListener;

    @Autowired
    private UserDeletionListener deletionListener;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AcademicianRepository academicianRepository;

    @Test
    void titlesComeFromTheCatalog() throws Exception {
        assertThat(AcademicTitle.parse("arş. gör.")).contains(AcademicTitle.RESEARCH_ASSISTANT);
        assertThat(AcademicTitle.parse("Dr.Öğr.Üyesi")).contains(AcademicTitle.ASSISTANT_PROFESSOR);
        assertThat(AcademicTitle.parse("lecturer_phd")).contains(AcademicTitle.LECTURER_PHD);
        assertThat(AcademicTitle.parse("ARS. GOR. DR.")).contains(AcademicTitle.RESEARCH_ASSISTANT_PHD);
        assertThat(AcademicTitle.parse("dr. ogr. uyesi")).contains(AcademicTitle.ASSISTANT_PROFESSOR);
        assertThat(AcademicTitle.parse("Profesör")).isEmpty();
        assertThat(AcademicTitle.RESEARCH_ASSISTANT_PHD.category()).isEqualTo(StaffCategory.RESEARCH_ASSISTANT);

        mockMvc.perform(get("/api/users/academic/titles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(AcademicTitle.values().length))
                .andExpect(jsonPath("$[0].code").value("PROFESSOR"))
                .andExpect(jsonPath("$[0].label").value("Prof. Dr."))
                .andExpect(jsonPath("$[0].category").value("FACULTY_MEMBER"));

        UUID teacher = academician("Doç. Dr.");
        assertThat(academicianRepository.findById(teacher).orElseThrow().getAcademicTitle()).isEqualTo(AcademicTitle.ASSOCIATE_PROFESSOR);
        mockMvc.perform(as(put("/api/users/profile/{id}", teacher), TestTokens.academician(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Hocam\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("OFFICIAL_FIELD_LOCKED"));
        mockMvc.perform(as(put("/api/users/profile/{id}", teacher), TestTokens.academician(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"doc. dr.\",\"officeHours\":\"Sali 10-12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Doç. Dr."))
                .andExpect(jsonPath("$.officeHours").value("Sali 10-12"))
                .andExpect(jsonPath("$.academicTitle").value("ASSOCIATE_PROFESSOR"))
                .andExpect(jsonPath("$.staffCategory").value("FACULTY_MEMBER"));

        UUID student = student(null);
        mockMvc.perform(as(put("/api/users/profile/{id}", student), TestTokens.student(student))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Prof. Dr.\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TITLE_NOT_ALLOWED"));
    }

    @Test
    void oneAccountCarriesBothAffiliations() throws Exception {
        UUID id = academician("Arş. Gör.");
        student(id);

        assertThat(studentRepository.existsById(id)).isTrue();
        mockMvc.perform(as(get("/api/users/profile/{id}", id), TestTokens.academician(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("Academician"))
                .andExpect(jsonPath("$.affiliations[0]").value("ACADEMICIAN"))
                .andExpect(jsonPath("$.affiliations[1]").value("STUDENT"))
                .andExpect(jsonPath("$.staffCategory").value("RESEARCH_ASSISTANT"))
                .andExpect(jsonPath("$.studentNumber").value("D" + id.toString().substring(0, 8)))
                .andExpect(jsonPath("$.entryYear").value(2025));
        mockMvc.perform(as(post("/api/users/internal/profiles/batch"), TestTokens.service("course-service"))
                        .contentType(MediaType.APPLICATION_JSON).content("[\"" + id + "\"]"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].affiliations.length()").value(2));
        mockMvc.perform(as(get("/api/users/internal/profiles/by-student-number/{no}", "D" + id.toString().substring(0, 8)),
                        TestTokens.service("auth-service")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("Academician"));

        mockMvc.perform(as(put("/api/users/profile/{id}", id), TestTokens.academician(id))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"Yeni\"}"))
                .andExpect(status().isOk());
        assertThat(studentRepository.findById(id).orElseThrow().getBio()).isEqualTo("Yeni");
        assertThat(academicianRepository.findById(id).orElseThrow().getBio()).isEqualTo("Yeni");

        deletionListener.handleUserDeletion(new UserDeletedMessage(id, "ACADEMICIAN", "test"));
        assertThat(studentRepository.existsById(id)).isFalse();
        assertThat(academicianRepository.existsById(id)).isFalse();
    }

    private UUID academician(String title) {
        UUID id = UUID.randomUUID();
        AcademicianProfileMessage message = new AcademicianProfileMessage();
        message.setUserId(id);
        message.setFirstName("Deniz");
        message.setLastName("Arslan");
        message.setEmail(id + "@test.educonnect.local");
        message.setTitle(title);
        creationListener.handleAcademicianProfileCreation(message);
        return id;
    }

    private UUID student(UUID existing) {
        UUID id = existing != null ? existing : UUID.randomUUID();
        UserRegisteredMessage message = new UserRegisteredMessage();
        message.setUserId(id);
        message.setFirstName("Deniz");
        message.setLastName("Arslan");
        message.setEmail(id + "@test.educonnect.local");
        message.setRoles(Set.of("ROLE_STUDENT"));
        message.setStudentNumber("D" + id.toString().substring(0, 8));
        message.setEntryYear(2025);
        creationListener.handleProfileCreation(message);
        return id;
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
