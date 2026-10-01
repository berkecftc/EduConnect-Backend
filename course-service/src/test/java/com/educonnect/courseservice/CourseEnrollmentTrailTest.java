package com.educonnect.courseservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseApplication;
import com.educonnect.courseservice.model.CourseApplicationStatus;
import com.educonnect.courseservice.model.Term;
import com.educonnect.courseservice.model.TermSeason;
import com.educonnect.courseservice.repository.CatalogCourseRepository;
import com.educonnect.courseservice.repository.CourseApplicationRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import com.educonnect.courseservice.repository.TermRepository;
import com.educonnect.courseservice.service.CourseLifecycleService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CourseIntegrationTest
class CourseEnrollmentTrailTest {

    private final UUID instructor = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
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
    private CourseApplicationRepository applicationRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private CourseLifecycleService lifecycleService;

    private Course course;

    @BeforeEach
    void openCourse() {
        course = courseRepository.save(TestCourses.offering(catalogRepository, "İzli Ders", instructor));
    }

    @Test
    void everyApplicationIsKeptAndPendingOnesCanBeWithdrawn() throws Exception {
        String first = apply().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        apply().andExpect(status().isConflict());
        mockMvc.perform(as(post("/api/courses/applications/{id}/withdraw", (String) JsonPath.read(first, "$.id")),
                        TestTokens.student(outsider)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(post("/api/courses/applications/{id}/withdraw", (String) JsonPath.read(first, "$.id")),
                        TestTokens.student(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));

        String second = apply().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(json(put("/api/courses/applications/{id}/reject", (String) JsonPath.read(second, "$.id")),
                        TestTokens.academician(instructor), "{\"rejectionReason\":\"Önkoşul eksik\"}"))
                .andExpect(status().isOk());
        String third = apply().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(as(put("/api/courses/applications/{id}/approve", (String) JsonPath.read(third, "$.id")),
                        TestTokens.academician(instructor)))
                .andExpect(status().isOk());

        mockMvc.perform(as(get("/api/courses/{id}/applications", course.getId()), TestTokens.academician(instructor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].status", contains("APPROVED", "REJECTED", "WITHDRAWN")));
        mockMvc.perform(as(get("/api/courses/{id}/applications", course.getId()).param("status", "REJECTED"),
                        TestTokens.academician(instructor)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].rejectionReason").value("Önkoşul eksik"));
        mockMvc.perform(as(get("/api/courses/{id}/applications", course.getId()), TestTokens.academician(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/courses/my-applications"), TestTokens.student(student)))
                .andExpect(jsonPath("$[?(@.courseId == '" + course.getId() + "')]", hasSize(3)));
    }

    @Test
    void withdrawalsAndRemovalsLeaveATrail() throws Exception {
        enrollThroughApplication();

        mockMvc.perform(json(post("/api/courses/{id}/withdraw", course.getId()), TestTokens.student(student),
                        "{\"reason\":\"Program çakışması\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WITHDRAWN"))
                .andExpect(jsonPath("$.reason").value("Program çakışması"))
                .andExpect(jsonPath("$.afterEnrollmentPeriod").value(false));
        mockMvc.perform(as(delete("/api/courses/{id}/withdraw", course.getId()), TestTokens.student(student)))
                .andExpect(status().isNotFound());
        assertThat(enrollmentRepository.findByCourseIdAndStudentId(course.getId(), student).orElseThrow().getWithdrawnAt())
                .isNotNull();

        enrollThroughApplication();
        String removal = "/api/courses/{id}/students/{student}/remove";
        mockMvc.perform(json(post(removal, course.getId(), student), TestTokens.academician(instructor), "{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(json(post(removal, course.getId(), student), TestTokens.academician(outsider), "{\"reason\":\"x\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post(removal, course.getId(), student), TestTokens.academician(instructor),
                        "{\"reason\":\"Devamsızlık\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("REMOVED"))
                .andExpect(jsonPath("$.actorId").value(instructor.toString()));

        mockMvc.perform(as(get("/api/courses/{id}/enrollment-history", course.getId()), TestTokens.academician(instructor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", contains("REMOVED", "ENROLLED", "WITHDRAWN", "ENROLLED")));
        mockMvc.perform(as(get("/api/courses/{id}/enrollment-history", course.getId()), TestTokens.student(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/courses/my-enrollment-history"), TestTokens.student(student)))
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].reason").value("Devamsızlık"))
                .andExpect(jsonPath("$[0].courseCode").value(course.getCode()));
    }

    @Test
    void theTermEnrollmentWindowGatesApplicationsAndMarksLateWithdrawals() throws Exception {
        LocalDate today = LocalDate.now();
        Term term = new Term(ThreadLocalRandom.current().nextInt(2100, 2190), TermSeason.SPRING);
        term.schedule(today.minusDays(30), today.plusDays(60), today.minusDays(30), today.plusDays(1));
        term = termRepository.save(term);
        course.setTermId(term.getId());
        course = courseRepository.save(course);
        enrollThroughApplication();

        term.schedule(today.minusDays(30), today.plusDays(60), today.minusDays(30), today.minusDays(1));
        termRepository.save(term);
        UUID late = UUID.randomUUID();
        mockMvc.perform(as(post("/api/courses/{id}/apply", course.getId()), TestTokens.student(late)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ENROLLMENT_CLOSED"));
        mockMvc.perform(as(post("/api/courses/{id}/withdraw", course.getId()), TestTokens.student(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.afterEnrollmentPeriod").value(true));
    }

    @Test
    void pendingApplicationsCloseWhenTheCourseCompletes() {
        LocalDate today = LocalDate.now();
        Term ended = new Term(ThreadLocalRandom.current().nextInt(2100, 2190), TermSeason.FALL);
        ended.schedule(today.minusMonths(4), today.minusDays(1), null, null);
        ended = termRepository.save(ended);
        course.setTermId(ended.getId());
        course = courseRepository.save(course);
        CourseApplication pending = applicationRepository.save(new CourseApplication(course.getId(), student));
        CourseApplication rejected = new CourseApplication(course.getId(), UUID.randomUUID());
        rejected.setStatus(CourseApplicationStatus.REJECTED);
        rejected = applicationRepository.save(rejected);

        assertThat(lifecycleService.advance(today).get("closedApplications")).isPositive();

        CourseApplication closed = applicationRepository.findById(pending.getId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(CourseApplicationStatus.CLOSED);
        assertThat(closed.getProcessedDate()).isNotNull();
        assertThat(applicationRepository.findById(rejected.getId()).orElseThrow().getStatus())
                .isEqualTo(CourseApplicationStatus.REJECTED);
    }

    private void enrollThroughApplication() throws Exception {
        String applied = apply().andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        mockMvc.perform(as(put("/api/courses/applications/{id}/approve", (String) JsonPath.read(applied, "$.id")),
                        TestTokens.academician(instructor)))
                .andExpect(status().isOk());
    }

    private ResultActions apply() throws Exception {
        return mockMvc.perform(as(post("/api/courses/{id}/apply", course.getId()), TestTokens.student(student)));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
