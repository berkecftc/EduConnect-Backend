package com.educonnect.courseservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.dto.UserSummaryDto;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseApplication;
import com.educonnect.courseservice.model.CourseStaffRole;
import com.educonnect.courseservice.model.CourseStatus;
import com.educonnect.courseservice.repository.CatalogCourseRepository;
import com.educonnect.courseservice.repository.CourseApplicationRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.CourseStaffRepository;
import com.educonnect.courseservice.service.UserDataCleanupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CourseIntegrationTest
class CourseStaffTest {

    private static final UUID RESEARCH_ASSISTANT = UUID.randomUUID();

    private final UUID coordinator = UUID.randomUUID();
    private final UUID coInstructor = UUID.randomUUID();
    private final UUID assistant = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CatalogCourseRepository catalogRepository;

    @Autowired
    private CourseStaffRepository staffRepository;

    @Autowired
    private CourseApplicationRepository applicationRepository;

    @Autowired
    private UserDataCleanupService cleanupService;

    @MockitoBean
    private UserClient userClient;

    private Course course;

    @BeforeEach
    void setUp() {
        Map<UUID, String> roles = Map.of(coordinator, "Academician", coInstructor, "Academician",
                assistant, "Academician", outsider, "Academician", student, "Student");
        when(userClient.getUserById(any())).thenAnswer(call -> user(call.getArgument(0), roles));
        when(userClient.getUsersByIds(any())).thenAnswer(call -> call.<Collection<UUID>>getArgument(0).stream()
                .map(id -> user(id, roles)).toList());
        course = courseRepository.save(TestCourses.offering(catalogRepository, "Kadrolu Ders", coordinator));
    }

    @Test
    void onlyTheCoordinatorBuildsTheStaffAndOnlyWithAcademicians() throws Exception {
        addStaff(outsider, coordinator, "INSTRUCTOR").andExpect(status().isCreated());
        addStaff(coInstructor, outsider, "INSTRUCTOR").andExpect(status().isForbidden());
        addStaff(student, coordinator, "ASSISTANT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STAFF_NOT_ACADEMICIAN"));
        addStaff(coInstructor, coordinator, "COORDINATOR")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("COORDINATOR_NOT_ASSIGNABLE"));
        addStaff(outsider, coordinator, "ASSISTANT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STAFF_EXISTS"));
        addStaff(coordinator, coordinator, "ASSISTANT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STAFF_EXISTS"));

        mockMvc.perform(get("/api/courses/{id}/staff", course.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(coordinator.toString()))
                .andExpect(jsonPath("$[0].role").value("COORDINATOR"))
                .andExpect(jsonPath("$[1].userId").value(outsider.toString()))
                .andExpect(jsonPath("$[1].role").value("INSTRUCTOR"));

        mockMvc.perform(json(put("/api/courses/{id}/staff/{user}", course.getId(), outsider),
                        TestTokens.academician(coordinator), "{\"role\":\"ASSISTANT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].role").value("ASSISTANT"));
        mockMvc.perform(json(put("/api/courses/{id}/staff/{user}", course.getId(), coordinator),
                        TestTokens.academician(coordinator), "{\"role\":\"ASSISTANT\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COORDINATOR_NOT_EDITABLE"));
    }

    @Test
    void staffRolesGrantDifferentCoursePermissions() throws Exception {
        addStaff(coInstructor, coordinator, "INSTRUCTOR").andExpect(status().isCreated());
        addStaff(assistant, coordinator, "ASSISTANT").andExpect(status().isCreated());
        CourseApplication application = applicationRepository.save(new CourseApplication(course.getId(), student));

        mockMvc.perform(as(get("/api/courses/{id}/applications/pending", course.getId()), TestTokens.academician(assistant)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(put("/api/courses/applications/{id}/approve", application.getId()), TestTokens.academician(coInstructor)))
                .andExpect(status().isOk());
        mockMvc.perform(json(post("/api/courses/{id}/announcements", course.getId()), TestTokens.academician(assistant),
                        "{\"title\":\"Asistan\",\"content\":\"Deneme\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post("/api/courses/{id}/announcements", course.getId()), TestTokens.academician(coInstructor),
                        "{\"title\":\"Eş hoca\",\"content\":\"Deneme\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(as(get("/api/courses/{id}/announcements", course.getId()), TestTokens.academician(assistant)))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/courses/{id}/enrolled-students", course.getId()), TestTokens.academician(assistant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentId").value(student.toString()));
        mockMvc.perform(as(get("/api/courses/{id}/enrolled-students", course.getId()), TestTokens.academician(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put("/api/courses/{id}", course.getId()), TestTokens.academician(coInstructor), "{\"capacity\":30}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post("/api/courses/{id}/staff", course.getId()), TestTokens.academician(coInstructor),
                        "{\"userId\":\"" + outsider + "\",\"role\":\"ASSISTANT\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(get("/api/courses/instructor/me/courses"), TestTokens.academician(assistant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(course.getId().toString()))
                .andExpect(jsonPath("$[0].staffRole").value("ASSISTANT"));
        mockMvc.perform(get("/api/courses/instructor/{id}", coInstructor))
                .andExpect(jsonPath("$[*].id", hasItem(course.getId().toString())));
        mockMvc.perform(get("/api/courses/instructor/{id}", assistant))
                .andExpect(jsonPath("$[*].id", not(hasItem(course.getId().toString()))));

        mockMvc.perform(as(get("/api/courses/internal/{id}/access/{user}", course.getId(), assistant),
                        TestTokens.service("assignment-service")))
                .andExpect(jsonPath("$.instructor").value(false))
                .andExpect(jsonPath("$.staffRole").value("ASSISTANT"));
        mockMvc.perform(as(get("/api/courses/internal/{id}/access/{user}", course.getId(), coInstructor),
                        TestTokens.service("assignment-service")))
                .andExpect(jsonPath("$.instructor").value(true))
                .andExpect(jsonPath("$.staffRole").value("INSTRUCTOR"));
        mockMvc.perform(as(get("/api/courses/internal/instructors/{id}/course-ids", assistant),
                        TestTokens.service("auth-services")))
                .andExpect(jsonPath("$[0]").value(course.getId().toString()));
    }

    @Test
    void draftStaffSeeTheDraftAndStaffLeaveOrAreRemoved() throws Exception {
        course.setStatus(CourseStatus.DRAFT);
        course = courseRepository.save(course);
        addStaff(assistant, coordinator, "ASSISTANT").andExpect(status().isCreated());
        addStaff(coInstructor, coordinator, "INSTRUCTOR").andExpect(status().isCreated());

        mockMvc.perform(as(get("/api/courses/{id}", course.getId()), TestTokens.academician(assistant))).andExpect(status().isOk());
        mockMvc.perform(as(get("/api/courses/{id}/staff", course.getId()), TestTokens.academician(outsider)))
                .andExpect(status().isNotFound());

        mockMvc.perform(as(delete("/api/courses/{id}/staff/{user}", course.getId(), coInstructor), TestTokens.academician(assistant)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(delete("/api/courses/{id}/staff/{user}", course.getId(), assistant), TestTokens.academician(assistant)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(delete("/api/courses/{id}/staff/{user}", course.getId(), coordinator), TestTokens.academician(coordinator)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COORDINATOR_NOT_EDITABLE"));
        mockMvc.perform(as(delete("/api/courses/{id}/staff/{user}", course.getId(), coInstructor), TestTokens.admin(admin)))
                .andExpect(status().isNoContent());
        assertThat(staffRepository.findByCourseIdOrderByCreatedAtAsc(course.getId())).isEmpty();
    }

    @Test
    void researchAssistantsTeachButNeverCoordinate() throws Exception {
        addStaff(RESEARCH_ASSISTANT, coordinator, "INSTRUCTOR").andExpect(status().isCreated());
        mockMvc.perform(json(put("/api/courses/{id}/coordinator", course.getId()), TestTokens.admin(admin),
                        "{\"userId\":\"" + RESEARCH_ASSISTANT + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESEARCH_ASSISTANT_NOT_COORDINATOR"));
        String json = "{\"title\":\"Arş Gör Dersi\",\"code\":\"RA-" + UUID.randomUUID().toString().substring(0, 6)
                + "\",\"credit\":3,\"capacity\":10,\"instructorId\":\"" + RESEARCH_ASSISTANT + "\"}";
        mockMvc.perform(as(multipart("/api/courses").file(new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE,
                        json.getBytes(StandardCharsets.UTF_8))), TestTokens.academician(RESEARCH_ASSISTANT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESEARCH_ASSISTANT_NOT_COORDINATOR"));
        assertThat(courseRepository.findById(course.getId()).orElseThrow().getInstructorId()).isEqualTo(coordinator);
    }

    @Test
    void onlyAnAdminTransfersTheCoordinatorRole() throws Exception {
        addStaff(coInstructor, coordinator, "INSTRUCTOR").andExpect(status().isCreated());
        String body = "{\"userId\":\"" + coInstructor + "\",\"previousCoordinatorRole\":\"INSTRUCTOR\"}";

        mockMvc.perform(json(put("/api/courses/{id}/coordinator", course.getId()), TestTokens.academician(coordinator), body))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put("/api/courses/{id}/coordinator", course.getId()), TestTokens.admin(admin),
                        "{\"userId\":\"" + student + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STAFF_NOT_ACADEMICIAN"));
        mockMvc.perform(json(put("/api/courses/{id}/coordinator", course.getId()), TestTokens.admin(admin), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(coInstructor.toString()))
                .andExpect(jsonPath("$[0].role").value("COORDINATOR"))
                .andExpect(jsonPath("$[1].userId").value(coordinator.toString()))
                .andExpect(jsonPath("$[1].role").value("INSTRUCTOR"));

        assertThat(courseRepository.findById(course.getId()).orElseThrow().getInstructorId()).isEqualTo(coInstructor);
        mockMvc.perform(json(put("/api/courses/{id}", course.getId()), TestTokens.academician(coordinator), "{\"capacity\":30}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(put("/api/courses/{id}", course.getId()), TestTokens.academician(coInstructor), "{\"capacity\":30}"))
                .andExpect(status().isOk());

        mockMvc.perform(json(put("/api/courses/{id}/coordinator", course.getId()), TestTokens.admin(admin),
                        "{\"userId\":\"" + outsider + "\"}"))
                .andExpect(status().isOk());
        assertThat(staffRepository.findByCourseIdAndUserId(course.getId(), coInstructor)).isEmpty();
        assertThat(staffRepository.findByCourseIdAndUserId(course.getId(), coordinator).orElseThrow().getRole())
                .isEqualTo(CourseStaffRole.INSTRUCTOR);
    }

    @Test
    void archivedStaffIsFrozenAndDeletedUsersLeaveTheStaff() throws Exception {
        addStaff(assistant, coordinator, "ASSISTANT").andExpect(status().isCreated());
        course.archive(Instant.now());
        course = courseRepository.save(course);

        addStaff(outsider, coordinator, "ASSISTANT")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COURSE_READ_ONLY"));
        mockMvc.perform(as(get("/api/courses/internal/instructors/{id}/course-ids", assistant),
                        TestTokens.service("auth-services")))
                .andExpect(jsonPath("$[*]", not(hasItem(course.getId().toString()))));

        cleanupService.deleteUserData(assistant);
        assertThat(staffRepository.findByCourseIdAndUserId(course.getId(), assistant)).isEmpty();
    }

    private ResultActions addStaff(UUID userId, UUID actor, String role) throws Exception {
        return mockMvc.perform(json(post("/api/courses/{id}/staff", course.getId()), TestTokens.academician(actor),
                "{\"userId\":\"" + userId + "\",\"role\":\"" + role + "\"}"));
    }

    private static UserSummaryDto user(UUID id, Map<UUID, String> roles) {
        UserSummaryDto user = new UserSummaryDto();
        user.setId(id);
        user.setFirstName("Ad");
        user.setLastName(id.toString().substring(0, 4));
        user.setRole(roles.getOrDefault(id, "Academician"));
        if (RESEARCH_ASSISTANT.equals(id)) {
            user.setStaffCategory("RESEARCH_ASSISTANT");
        }
        return user;
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
