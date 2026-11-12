package com.educonnect.courseservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.dto.UserSummaryDto;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseStaff;
import com.educonnect.courseservice.model.CourseStaffRole;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.repository.CatalogCourseRepository;
import com.educonnect.courseservice.repository.CourseApplicationRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.CourseStaffRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CourseIntegrationTest
class CourseNotificationTest {

    private final UUID coordinator = UUID.randomUUID();
    private final UUID coInstructor = UUID.randomUUID();
    private final UUID assistant = UUID.randomUUID();
    private final UUID newcomer = UUID.randomUUID();
    private final UUID enrolled = UUID.randomUUID();
    private final UUID applicant = UUID.randomUUID();

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

    @Autowired
    private CourseApplicationRepository applicationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private UserClient userClient;

    private Course course;

    @BeforeEach
    void setUp() {
        when(userClient.getUserById(any())).thenAnswer(call -> academician(call.getArgument(0)));
        when(userClient.getUsersByIds(any())).thenAnswer(call -> call.<Collection<UUID>>getArgument(0).stream()
                .map(CourseNotificationTest::academician).toList());
        course = courseRepository.save(TestCourses.offering(catalogRepository, "Bildirimli Ders", coordinator));
        staffRepository.save(new CourseStaff(course.getId(), coInstructor, CourseStaffRole.INSTRUCTOR, coordinator));
        staffRepository.save(new CourseStaff(course.getId(), assistant, CourseStaffRole.ASSISTANT, coordinator));
        enrollmentRepository.save(new StudentCourseEnrollment(course.getId(), enrolled));
    }

    @Test
    void applicationsReachTheTeachersAndDecisionsTheStudent() throws Exception {
        when(userClient.getUserById(applicant)).thenReturn(null);
        mockMvc.perform(as(post("/api/courses/{id}/apply", course.getId()), TestTokens.student(applicant)))
                .andExpect(status().is2xxSuccessful());
        assertThat(notifications("COURSE_APPLICATION")).singleElement().asString()
                .contains(coordinator.toString(), coInstructor.toString(), "\"category\":\"COURSE\"", "Yeni ders başvurusu",
                        "\"link\":\"/courses/" + course.getId() + "\"")
                .doesNotContain(assistant.toString(), applicant.toString());

        UUID applicationId = applicationRepository.findAll().stream()
                .filter(application -> application.getCourseId().equals(course.getId())).findFirst().orElseThrow().getId();
        mockMvc.perform(json(put("/api/courses/applications/{id}/reject", applicationId), TestTokens.academician(coInstructor),
                        "{\"rejectionReason\":\"Önkoşul eksik\"}"))
                .andExpect(status().isOk());
        assertThat(notifications("COURSE_APPLICATION_REJECTED")).singleElement().asString()
                .contains(applicant.toString(), "Ders başvurunuz reddedildi");
    }

    @Test
    void staffChangesAndRemovalsTellTheAffectedPerson() throws Exception {
        mockMvc.perform(json(post("/api/courses/{id}/staff", course.getId()), TestTokens.academician(coordinator),
                        "{\"userId\":\"" + newcomer + "\",\"role\":\"ASSISTANT\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(json(put("/api/courses/{id}/staff/{user}", course.getId(), newcomer), TestTokens.academician(coordinator),
                        "{\"role\":\"INSTRUCTOR\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(delete("/api/courses/{id}/staff/{user}", course.getId(), assistant), TestTokens.academician(assistant)))
                .andExpect(status().is2xxSuccessful());
        mockMvc.perform(json(post("/api/courses/{id}/students/{student}/remove", course.getId(), enrolled),
                        TestTokens.academician(coordinator), "{\"reason\":\"Devamsızlık\"}"))
                .andExpect(status().isOk());

        List<String> staff = notifications("COURSE_STAFF");
        assertThat(staff).hasSize(2).allSatisfy(body -> assertThat(body).contains(newcomer.toString()));
        assertThat(staff).anySatisfy(body -> assertThat(body).contains("asistan olarak eklendiniz"));
        assertThat(staff).anySatisfy(body -> assertThat(body).contains("öğretim elemanı olarak değiştirildi"));
        assertThat(notifications("COURSE_REMOVED")).singleElement().asString()
                .contains(enrolled.toString(), "Dersten çıkarıldınız", "Devamsızlık");
    }

    @Test
    void onlyVisibleMaterialsAreAnnouncedToEnrolledStudents() throws Exception {
        material("{\"title\":\"Gizli çözüm\",\"kind\":\"LINK\",\"linkUrl\":\"https://ornek.edu.tr/c\",\"visible\":false}");
        assertThat(notifications("COURSE_MATERIAL")).isEmpty();

        material("{\"title\":\"Hafta 1 okuma\",\"section\":\"Hafta 1\",\"kind\":\"LINK\",\"linkUrl\":\"https://ornek.edu.tr/o\"}");
        assertThat(notifications("COURSE_MATERIAL")).singleElement().asString()
                .contains(enrolled.toString(), "Yeni ders materyali: Hafta 1 okuma", "(Hafta 1)");

        UUID hidden = jdbcTemplate.queryForObject("select id from course_db.course_materials where course_id = ? and title = ?",
                UUID.class, course.getId(), "Gizli çözüm");
        mockMvc.perform(json(put("/api/courses/{id}/materials/{material}", course.getId(), hidden),
                        TestTokens.academician(coordinator), "{\"visible\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(json(put("/api/courses/{id}/materials/{material}", course.getId(), hidden),
                        TestTokens.academician(coordinator), "{\"title\":\"Gizli çözüm\"}"))
                .andExpect(status().isOk());
        assertThat(notifications("COURSE_MATERIAL")).hasSize(2)
                .anySatisfy(body -> assertThat(body).contains("Yeni ders materyali: Gizli çözüm"));
    }

    private void material(String json) throws Exception {
        mockMvc.perform(as(multipart("/api/courses/{id}/materials", course.getId())
                        .file(new MockMultipartFile("material", "", MediaType.APPLICATION_JSON_VALUE,
                                json.getBytes(StandardCharsets.UTF_8))), TestTokens.academician(coordinator)))
                .andExpect(status().isCreated());
    }

    private List<String> notifications(String type) {
        return jdbcTemplate.queryForList("select convert_from(body, 'UTF8') from course_db.outbox_messages "
                        + "where routing_key = 'notification.request' and convert_from(body, 'UTF8') like ? "
                        + "and convert_from(body, 'UTF8') like ?",
                String.class, "%" + course.getId() + "%", "%\"type\":\"" + type + "\"%");
    }

    private static UserSummaryDto academician(UUID id) {
        UserSummaryDto user = new UserSummaryDto();
        user.setId(id);
        user.setFirstName("Ad");
        user.setLastName("Soyad");
        user.setRole("Academician");
        return user;
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
