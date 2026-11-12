package com.educonnect.assignmentservice;

import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.common.test.TestTokens;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AssignmentIntegrationTest
class AssessmentModelTest {

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructor = UUID.randomUUID();
    private final UUID assistant = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SubmissionRepository submissionRepository;

    @BeforeEach
    void course() {
        FakeCourseService.course(courseId, instructor, student);
        FakeCourseService.staff(courseId, assistant, "ASSISTANT");
    }

    @Test
    void assessmentsCarryTypeWeightAndMaximumPointsWithinOneHundredPercent() throws Exception {
        create("Vize", "\"type\":\"MIDTERM\",\"weight\":40,\"maxPoints\":50")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("MIDTERM"))
                .andExpect(jsonPath("$.weight").value(40))
                .andExpect(jsonPath("$.maxPoints").value(50));
        create("Final", "\"type\":\"FINAL\",\"weight\":70")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WEIGHT_EXCEEDED"));
        create("Final", "\"type\":\"FINAL\",\"weight\":60").andExpect(status().isOk());
        create("Alıştırma", "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("HOMEWORK"))
                .andExpect(jsonPath("$.weight").value(0))
                .andExpect(jsonPath("$.maxPoints").value(100));

        mockMvc.perform(as(get("/api/assignments/my-assignments"), TestTokens.student(student)))
                .andExpect(jsonPath("$[?(@.courseId == '" + courseId + "')].type", containsInAnyOrder("MIDTERM", "FINAL", "HOMEWORK")));
    }

    @Test
    void gradesStayWithinTheMaximumAndEditsAreRecorded() throws Exception {
        String assignmentId = idOf(create("Kısa sınav", "\"type\":\"QUIZ\",\"weight\":10,\"maxPoints\":20"));
        UUID submissionId = submissionRepository.save(new AssignmentSubmission(UUID.fromString(assignmentId), student, null, false)).getId();

        grade(submissionId, "21").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_GRADE"));
        grade(submissionId, "17.5").andExpect(status().isOk());
        assertThat(submissionRepository.findById(submissionId).orElseThrow().getGrade()).isEqualByComparingTo("17.5");

        update(assignmentId, TestTokens.academician(instructor), "{\"maxPoints\":15}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GRADES_EXCEED_MAX"));
        update(assignmentId, TestTokens.academician(assistant), "{\"weight\":15}").andExpect(status().isForbidden());
        update(assignmentId, TestTokens.academician(instructor), "{\"weight\":101}").andExpect(status().isBadRequest());
        String dueDate = LocalDateTime.now().plusDays(10).withNano(0).toString();
        update(assignmentId, TestTokens.academician(instructor),
                "{\"title\":\"Kısa sınav 1\",\"weight\":15,\"maxPoints\":25,\"dueDate\":\"" + dueDate + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Kısa sınav 1"))
                .andExpect(jsonPath("$.weight").value(15))
                .andExpect(jsonPath("$.maxPoints").value(25));

        mockMvc.perform(as(get("/api/assignments/{id}/changes", assignmentId), TestTokens.academician(assistant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].field", containsInAnyOrder("title", "weight", "maxPoints", "dueDate")))
                .andExpect(jsonPath("$[?(@.field == 'weight')].oldValue").value("10"))
                .andExpect(jsonPath("$[?(@.field == 'weight')].changedBy").value(instructor.toString()));
        mockMvc.perform(as(get("/api/assignments/{id}/changes", assignmentId), TestTokens.student(student)))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(delete("/api/assignments/{id}", assignmentId), TestTokens.academician(instructor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ASSIGNMENT_HAS_SUBMISSIONS"));
    }

    private ResultActions create(String title, String extra) throws Exception {
        String json = "{\"title\":\"" + title + "\",\"dueDate\":\"" + LocalDateTime.now().plusDays(3).withNano(0)
                + "\",\"courseId\":\"" + courseId + "\"" + (extra.isEmpty() ? "" : "," + extra) + "}";
        MockMultipartFile part = new MockMultipartFile("assignment", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(as(multipart("/api/assignments").file(part), TestTokens.academician(instructor)));
    }

    private ResultActions grade(UUID submissionId, String grade) throws Exception {
        return mockMvc.perform(as(put("/api/assignments/submissions/{id}/grade", submissionId), TestTokens.academician(instructor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"grade\":" + grade + "}"));
    }

    private ResultActions update(String assignmentId, String token, String body) throws Exception {
        return mockMvc.perform(as(put("/api/assignments/{id}", assignmentId), token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String idOf(ResultActions result) throws Exception {
        return JsonPath.read(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
