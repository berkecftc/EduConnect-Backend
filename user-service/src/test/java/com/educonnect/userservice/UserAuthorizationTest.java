package com.educonnect.userservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.userservice.repository.AcademicianRepository;
import com.educonnect.userservice.repository.ArchivedStudentRepository;
import com.educonnect.userservice.repository.StudentRepository;
import com.educonnect.userservice.models.Academician;
import com.educonnect.userservice.models.ArchivedStudent;
import com.educonnect.userservice.models.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UserIntegrationTest
class UserAuthorizationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    private final UUID owner = UUID.randomUUID();
    private final UUID otherStudent = UUID.randomUUID();
    private final UUID academician = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AcademicianRepository academicianRepository;

    @Autowired
    private ArchivedStudentRepository archivedStudentRepository;

    @BeforeEach
    void createProfiles() {
        studentRepository.save(student(owner, "Ayşe"));
        studentRepository.save(student(otherStudent, "Mehmet"));
        Academician teacher = new Academician(academician, "Zeynep", "Hoca", "Dr.");
        teacher.setEmail(academician + "@test.educonnect.local");
        academicianRepository.save(teacher);
    }

    @Test
    void aProfileIsUpdatedOnlyByItsOwner() throws Exception {
        String body = "{\"firstName\":\"Ele Geçirildi\"}";
        for (String token : new String[]{TestTokens.student(otherStudent), TestTokens.academician(academician), TestTokens.admin(admin)}) {
            mockMvc.perform(as(put("/api/users/profile/{id}", owner), token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        }
        assertThat(studentRepository.findById(owner).orElseThrow().getFirstName()).isEqualTo("Ayşe");

        mockMvc.perform(as(put("/api/users/profile/{id}", owner), TestTokens.student(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"Merhaba\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bio").value("Merhaba"));
        assertThat(studentRepository.findById(owner).orElseThrow().getBio()).isEqualTo("Merhaba");
    }

    @Test
    void theMultipartProfileUpdateIsAlsoSelfOnly() throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart(HttpMethod.PUT, "/api/users/profile/{id}", owner)
                .file(new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, PNG))
                .param("firstName", "Ele Geçirildi");

        mockMvc.perform(as(request, TestTokens.student(otherStudent)))
                .andExpect(status().isForbidden());
        Student unchanged = studentRepository.findById(owner).orElseThrow();
        assertThat(unchanged.getFirstName()).isEqualTo("Ayşe");
        assertThat(unchanged.getProfileImageUrl()).isNull();
    }

    @Test
    void theProfilePictureEndpointOnlyTouchesTheCallersProfile() throws Exception {
        MockMultipartFile picture = new MockMultipartFile("file", "avatar.png", MediaType.IMAGE_PNG_VALUE, PNG);

        mockMvc.perform(as(multipart("/api/users/me/profile-picture").file(picture), TestTokens.student(owner)))
                .andExpect(status().isOk());
        assertThat(studentRepository.findById(owner).orElseThrow().getProfileImageUrl()).isNotNull();
        assertThat(studentRepository.findById(otherStudent).orElseThrow().getProfileImageUrl()).isNull();

        mockMvc.perform(as(multipart("/api/users/me/profile-picture").file(picture), TestTokens.admin(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void archivedListsAreAdminOnly() throws Exception {
        archivedStudentRepository.save(new ArchivedStudent(UUID.randomUUID(), "Eski", "Öğrenci", "S-" + UUID.randomUUID(),
                "Bilgisayar", null, Instant.now(), "test"));

        for (String path : new String[]{"/api/users/students/archived", "/api/users/academicians/archived"}) {
            mockMvc.perform(as(get(path), TestTokens.student(owner)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
            mockMvc.perform(as(get(path), TestTokens.academician(academician)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized());
            mockMvc.perform(as(get(path), TestTokens.admin(admin)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(as(get("/api/users/students/archived"), TestTokens.admin(admin)))
                .andExpect(jsonPath("$[?(@.firstName == 'Eski')]").exists());
    }

    @Test
    void emailIsVisibleOnlyToTheOwnerAndAdmins() throws Exception {
        String email = owner + "@test.educonnect.local";

        mockMvc.perform(as(get("/api/users/profile/{id}", owner), TestTokens.student(otherStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Ayşe"))
                .andExpect(jsonPath("$.email").doesNotExist());
        mockMvc.perform(as(get("/api/users/profile/{id}", owner), TestTokens.academician(academician)))
                .andExpect(jsonPath("$.email").doesNotExist());
        mockMvc.perform(get("/api/users/profile/{id}", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist());
        mockMvc.perform(get("/api/users/profile/{id}", owner)
                        .header("X-Authenticated-User-Id", owner.toString())
                        .header("X-Authenticated-User-Roles", "ROLE_ADMIN"))
                .andExpect(jsonPath("$.email").doesNotExist());

        mockMvc.perform(as(get("/api/users/profile/{id}", owner), TestTokens.student(owner)))
                .andExpect(jsonPath("$.email").value(email));
        mockMvc.perform(as(get("/api/users/profile/{id}", owner), TestTokens.admin(admin)))
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void theAggregatedProfileHidesEmailFromOthers() throws Exception {
        mockMvc.perform(as(get("/api/users/profile/{id}/aggregated", owner), TestTokens.student(otherStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist());
        mockMvc.perform(as(get("/api/users/profile/{id}/aggregated", owner), TestTokens.student(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(owner + "@test.educonnect.local"));
    }

    @Test
    void internalProfileEndpointsAcceptOnlyServiceTokens() throws Exception {
        String batch = "[\"" + owner + "\",\"" + otherStudent + "\"]";

        mockMvc.perform(post("/api/users/internal/profiles/batch").contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        mockMvc.perform(as(post("/api/users/internal/profiles/batch"), TestTokens.student(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/users/internal/profiles/batch"), TestTokens.admin(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(as(post("/api/users/internal/profiles/batch"), TestTokens.service("assignment-service"))
                        .contentType(MediaType.APPLICATION_JSON).content(batch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(as(get("/api/users/internal/profiles/{id}", owner), TestTokens.admin(admin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/users/internal/profiles/{id}", owner), TestTokens.service("club-service")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(owner + "@test.educonnect.local"));
    }

    @Test
    void aServiceTokenIsIgnoredOnPublicPaths() throws Exception {
        mockMvc.perform(as(put("/api/users/profile/{id}", owner), TestTokens.service("club-service"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Servis\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(studentRepository.findById(owner).orElseThrow().getFirstName()).isEqualTo("Ayşe");
    }

    @Test
    void updatesWithoutATokenAreUnauthenticatedEvenWithAForgedIdentityHeader() throws Exception {
        mockMvc.perform(put("/api/users/profile/{id}", owner)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Anonim\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/users/profile/{id}", owner)
                        .header("X-Authenticated-User-Id", owner.toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Sahte\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(studentRepository.findById(owner).orElseThrow().getFirstName()).isEqualTo("Ayşe");
    }

    @Test
    void academicianSearchIsPublicAndClosedLookupsAreGone() throws Exception {
        mockMvc.perform(get("/api/users/search/academicians").param("query", "Zeynep"))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/users/by-student-number/{no}", "S-1"), TestTokens.admin(admin)))
                .andExpect(status().isGone());
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }

    private static Student student(UUID id, String firstName) {
        Student student = new Student(id, firstName, "Test", "S-" + id.toString().substring(0, 12));
        student.setEmail(id + "@test.educonnect.local");
        return student;
    }
}
