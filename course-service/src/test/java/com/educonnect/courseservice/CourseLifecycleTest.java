package com.educonnect.courseservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.courseservice.service.StaffEligibility;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseStatus;
import com.educonnect.courseservice.model.StudentCourseEnrollment;
import com.educonnect.courseservice.model.Term;
import com.educonnect.courseservice.model.TermSeason;
import com.educonnect.courseservice.repository.CatalogCourseRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import com.educonnect.courseservice.repository.TermRepository;
import com.educonnect.courseservice.service.CourseLifecycleService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CourseIntegrationTest
class CourseLifecycleTest {

    @MockitoBean
    private StaffEligibility staffEligibility;

    private final UUID instructor = UUID.randomUUID();
    private final UUID otherInstructor = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CatalogCourseRepository catalogRepository;

    @Autowired
    private TermRepository termRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseLifecycleService lifecycleService;

    @Test
    void draftCoursesStayHiddenUntilPublished() throws Exception {
        String created = mockMvc.perform(as(multipart("/api/courses").file(coursePart(true)), TestTokens.academician(instructor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        String courseId = JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/courses")).andExpect(jsonPath("$[*].id", not(hasItem(courseId))));
        mockMvc.perform(as(get("/api/courses/{id}", courseId), TestTokens.student(student))).andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/courses/{id}", courseId), TestTokens.academician(instructor))).andExpect(status().isOk());
        mockMvc.perform(as(post("/api/courses/{id}/apply", courseId), TestTokens.student(student)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COURSE_NOT_OPEN"));

        mockMvc.perform(as(post("/api/courses/{id}/publish", courseId), TestTokens.academician(otherInstructor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/courses/{id}/publish", courseId), TestTokens.academician(instructor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(get("/api/courses")).andExpect(jsonPath("$[*].id", hasItem(courseId)));
        mockMvc.perform(as(post("/api/courses/{id}/apply", courseId), TestTokens.student(student)))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void runningCoursesCanBeUpdatedButNotBelowTheEnrolledCount() throws Exception {
        Course course = courseRepository.save(TestCourses.offering(catalogRepository, "Güncellenen Ders", instructor));
        enroll(course, UUID.randomUUID());
        enroll(course, UUID.randomUUID());

        mockMvc.perform(json(put("/api/courses/{id}", course.getId()), TestTokens.academician(otherInstructor), "{\"capacity\":5}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put("/api/courses/{id}", course.getId()), TestTokens.academician(instructor), "{\"capacity\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CAPACITY_BELOW_ENROLLED"));
        mockMvc.perform(json(put("/api/courses/{id}", course.getId()), TestTokens.academician(instructor),
                        "{\"capacity\":40,\"description\":\"Yeni açıklama\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(40))
                .andExpect(jsonPath("$.description").value("Yeni açıklama"));
    }

    @Test
    void coursesCompleteAtTermEndAndBecomeReadOnlyWhenArchived() throws Exception {
        LocalDate today = LocalDate.now();
        Term ended = new Term(ThreadLocalRandom.current().nextInt(2100, 2190), TermSeason.SUMMER);
        ended.schedule(today.minusMonths(3), today.minusDays(1), null, null);
        ended = termRepository.save(ended);
        Course course = TestCourses.offering(catalogRepository, "Biten Ders", instructor);
        course.setTermId(ended.getId());
        course = courseRepository.save(course);
        Course upcoming = TestCourses.offering(catalogRepository, "Açılacak Ders", instructor);
        upcoming.setStatus(CourseStatus.OPEN);
        upcoming = courseRepository.save(upcoming);

        lifecycleService.advance(today);
        assertThat(statusOf(course)).isEqualTo(CourseStatus.COMPLETED);
        assertThat(statusOf(upcoming)).isEqualTo(CourseStatus.ACTIVE);

        mockMvc.perform(as(post("/api/courses/{id}/apply", course.getId()), TestTokens.student(student)))
                .andExpect(status().isConflict());
        mockMvc.perform(json(put("/api/courses/{id}", course.getId()), TestTokens.academician(instructor), "{\"capacity\":50}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COURSE_READ_ONLY"));
        mockMvc.perform(json(post("/api/courses/{id}/announcements", course.getId()), TestTokens.academician(instructor),
                        "{\"title\":\"Notlar\",\"content\":\"Puanlar girildi.\"}"))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(as(post("/api/courses/{id}/archive", course.getId()), TestTokens.academician(instructor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));
        mockMvc.perform(json(post("/api/courses/{id}/announcements", course.getId()), TestTokens.academician(instructor),
                        "{\"title\":\"Geç\",\"content\":\"x\"}"))
                .andExpect(status().isConflict());
        mockMvc.perform(as(get("/api/courses/internal/{id}/access/{user}", course.getId(), instructor),
                        TestTokens.service("assignment-service")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.instructor").value(true))
                .andExpect(jsonPath("$.enrolled").value(false));
    }

    @Test
    void completedCoursesAreArchivedAfterTheGracePeriod() {
        LocalDate today = LocalDate.now();
        Term ended = new Term(ThreadLocalRandom.current().nextInt(2100, 2190), TermSeason.FALL);
        ended.schedule(today.minusMonths(6), today.minusDays(40), null, null);
        ended = termRepository.save(ended);
        Course course = TestCourses.offering(catalogRepository, "Eski Ders", instructor);
        course.setTermId(ended.getId());
        course.setStatus(CourseStatus.COMPLETED);
        course = courseRepository.save(course);

        lifecycleService.advance(today);
        assertThat(statusOf(course)).isEqualTo(CourseStatus.ARCHIVED);
        lifecycleService.advance(today.plusYears(20));
        assertThat(courseRepository.existsById(course.getId())).isTrue();
    }

    private CourseStatus statusOf(Course course) {
        return courseRepository.findById(course.getId()).orElseThrow().getStatus();
    }

    private void enroll(Course course, UUID studentId) {
        StudentCourseEnrollment enrollment = new StudentCourseEnrollment();
        enrollment.setCourseId(course.getId());
        enrollment.setStudentId(studentId);
        enrollmentRepository.save(enrollment);
    }

    private MockMultipartFile coursePart(boolean draft) {
        String json = "{\"title\":\"Taslak Ders\",\"code\":\"DRF" + ThreadLocalRandom.current().nextInt(100000, 999999)
                + "\",\"credit\":3,\"capacity\":10,\"instructorId\":\"" + instructor + "\",\"draft\":" + draft + "}";
        return new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
