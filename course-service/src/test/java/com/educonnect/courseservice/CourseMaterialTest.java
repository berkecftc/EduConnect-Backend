package com.educonnect.courseservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseStaff;
import com.educonnect.courseservice.model.CourseStaffRole;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.repository.CatalogCourseRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.CourseStaffRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CourseIntegrationTest
class CourseMaterialTest {

    private final UUID instructor = UUID.randomUUID();
    private final UUID assistant = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CatalogCourseRepository catalogRepository;

    @Autowired
    private CourseStaffRepository staffRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private Course course;

    @BeforeEach
    void course() {
        course = courseRepository.save(TestCourses.offering(catalogRepository, "Materyalli Ders", instructor));
        staffRepository.save(new CourseStaff(course.getId(), assistant, CourseStaffRole.ASSISTANT, instructor));
        enrollmentRepository.save(new StudentCourseEnrollment(course.getId(), student));
    }

    @Test
    void teachersShareFilesAndLinksThatEnrolledStudentsSee() throws Exception {
        create(TestTokens.academician(instructor), "{\"title\":\"Ders notu 1\",\"section\":\"Hafta 1\",\"kind\":\"FILE\"}", "notlar")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fileName").value("hafta1.pdf"));
        create(TestTokens.academician(instructor),
                "{\"title\":\"Okuma\",\"section\":\"Hafta 1\",\"sortOrder\":1,\"kind\":\"LINK\",\"linkUrl\":\"https://ornek.edu.tr/okuma\"}", null)
                .andExpect(status().isCreated());
        String hidden = create(TestTokens.academician(instructor),
                "{\"title\":\"Çözümler\",\"section\":\"Hafta 2\",\"kind\":\"FILE\",\"visible\":false}", "cozum")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String hiddenId = JsonPath.read(hidden, "$.id");

        create(TestTokens.academician(assistant), "{\"title\":\"Asistan\",\"kind\":\"LINK\",\"linkUrl\":\"https://ornek.edu.tr\"}", null)
                .andExpect(status().isForbidden());
        create(TestTokens.academician(instructor), "{\"title\":\"Kötü\",\"kind\":\"LINK\",\"linkUrl\":\"javascript:alert(1)\"}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_MATERIAL_LINK"));
        create(TestTokens.academician(instructor), "{\"title\":\"Dosyasız\",\"kind\":\"FILE\"}", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MATERIAL_FILE_REQUIRED"));

        mockMvc.perform(as(get("/api/courses/{id}/materials", course.getId()), TestTokens.student(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Ders notu 1", "Okuma")));
        mockMvc.perform(as(get("/api/courses/{id}/materials", course.getId()), TestTokens.academician(assistant)))
                .andExpect(jsonPath("$", hasSize(3)));
        mockMvc.perform(as(get("/api/courses/{id}/materials", course.getId()), TestTokens.student(outsider)))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(get("/api/courses/{id}/materials/{m}/file", course.getId(), hiddenId), TestTokens.student(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put("/api/courses/{id}/materials/{m}", course.getId(), hiddenId), TestTokens.academician(instructor),
                        "{\"visible\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visible").value(true));
        mockMvc.perform(as(get("/api/courses/{id}/materials/{m}/file", course.getId(), hiddenId), TestTokens.student(student)))
                .andExpect(status().isOk())
                .andExpect(content().string("cozum"));
        mockMvc.perform(as(get("/api/courses/{id}/materials/{m}/file", course.getId(), hiddenId), TestTokens.student(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put("/api/courses/{id}/materials/{m}", course.getId(), hiddenId), TestTokens.academician(instructor),
                        "{\"linkUrl\":\"https://ornek.edu.tr\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MATERIAL_NOT_LINK"));

        mockMvc.perform(as(delete("/api/courses/{id}/materials/{m}", course.getId(), hiddenId), TestTokens.academician(instructor)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(get("/api/courses/{id}/materials", course.getId()), TestTokens.student(student)))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void archivedCoursesKeepTheirMaterialsReadOnly() throws Exception {
        create(TestTokens.academician(instructor), "{\"title\":\"Kalıcı\",\"kind\":\"LINK\",\"linkUrl\":\"https://ornek.edu.tr\"}", null)
                .andExpect(status().isCreated());
        course.archive(Instant.now());
        course = courseRepository.save(course);

        create(TestTokens.academician(instructor), "{\"title\":\"Geç\",\"kind\":\"LINK\",\"linkUrl\":\"https://ornek.edu.tr\"}", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COURSE_READ_ONLY"));
        mockMvc.perform(as(get("/api/courses/{id}/materials", course.getId()), TestTokens.student(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    private ResultActions create(String token, String json, String fileContent) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/courses/{id}/materials", course.getId())
                .file(new MockMultipartFile("material", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8)));
        if (fileContent != null) {
            String name = fileContent.equals("notlar") ? "hafta1.pdf" : "cozum.txt";
            String type = fileContent.equals("notlar") ? MediaType.APPLICATION_PDF_VALUE : MediaType.TEXT_PLAIN_VALUE;
            request.file(new MockMultipartFile("file", name, type, fileContent.getBytes(StandardCharsets.UTF_8)));
        }
        return mockMvc.perform(as(request, token));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
