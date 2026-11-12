package com.educonnect.assignmentservice;

import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
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
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AssignmentIntegrationTest
class DeadlinePolicyTest {

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructor = UUID.randomUUID();
    private final UUID assistant = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID classmate = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private final LocalDateTime due = LocalDateTime.now().minusHours(1).withSecond(30).withNano(0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    private UUID assignmentId;

    @BeforeEach
    void lateAssignment() {
        FakeCourseService.course(courseId, instructor, student, classmate);
        FakeCourseService.staff(courseId, assistant, "ASSISTANT");
        Assignment assignment = new Assignment();
        assignment.setTitle("Geç Teslimli Ödev");
        assignment.setCourseId(courseId);
        assignment.setDueDate(due);
        assignment.setLateUntil(due.plusDays(1));
        assignment.setLatePenaltyPercent(BigDecimal.valueOf(20));
        assignmentId = assignmentRepository.save(assignment).getId();
    }

    @Test
    void lateWorkIsAcceptedUntilTheLateDeadlineWithAPenalty() throws Exception {
        submit(student, null, " ").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("SUBMISSION_EMPTY"));
        String submitted = submit(student, null, "Geç cevabım")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.late").value(true))
                .andExpect(jsonPath("$.textContent").value("Geç cevabım"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        submit(student, null, "Düzeltme").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("RESUBMISSION_CLOSED"));
        String submissionId = JsonPath.read(submitted, "$.id");

        mockMvc.perform(as(put("/api/assignments/submissions/{id}/grade", submissionId), TestTokens.academician(instructor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"grade\":80}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/assignments/course/{id}/gradebook", courseId), TestTokens.academician(assistant)))
                .andExpect(jsonPath("$.students[?(@.studentId == '" + student + "')].grades[0].finalGrade").value(64.0));
        mockMvc.perform(as(post("/api/assignments/{id}/publish-grades", assignmentId), TestTokens.academician(instructor)))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/assignments/my-assignments"), TestTokens.student(student)))
                .andExpect(jsonPath("$[0].latePenaltyPercent").value(20.0))
                .andExpect(jsonPath("$[0].submission.grade").value(80.0))
                .andExpect(jsonPath("$[0].submission.finalGrade").value(64.0))
                .andExpect(jsonPath("$[0].submission.textContent").value("Geç cevabım"));
    }

    @Test
    void extensionsMoveOneStudentsDeadlineAndKeepVersions() throws Exception {
        String path = "/api/assignments/{id}/extensions/{student}";
        String body = "{\"dueDate\":\"" + due.plusDays(2) + "\",\"reason\":\"Sağlık raporu\"}";
        mockMvc.perform(json(put(path, assignmentId, classmate), TestTokens.academician(assistant), body)).andExpect(status().isForbidden());
        mockMvc.perform(json(put(path, assignmentId, outsider), TestTokens.academician(instructor), body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_ENROLLED"));
        mockMvc.perform(json(put(path, assignmentId, classmate), TestTokens.academician(instructor),
                        "{\"dueDate\":\"" + due.minusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("EXTENSION_NOT_LATER"));
        mockMvc.perform(json(put(path, assignmentId, classmate), TestTokens.academician(instructor), body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value(due.plusDays(2).toString()))
                .andExpect(jsonPath("$.lateUntil").value(due.plusDays(3).toString()));
        mockMvc.perform(as(get("/api/assignments/{id}/extensions", assignmentId), TestTokens.academician(assistant)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reason").value("Sağlık raporu"));

        MockMultipartFile first = new MockMultipartFile("file", "ilk.txt", MediaType.TEXT_PLAIN_VALUE, "ilk".getBytes(StandardCharsets.UTF_8));
        String firstResponse = submit(classmate, first, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.late").value(false))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String firstFile = JsonPath.read(firstResponse, "$.submissionFileUrl");
        String submissionId = JsonPath.read(firstResponse, "$.id");
        submit(classmate, null, "İkinci sürüm").andExpect(status().isCreated());

        mockMvc.perform(as(get("/api/assignments/submissions/{id}/versions", submissionId), TestTokens.student(classmate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].versionNo", contains(2, 1)))
                .andExpect(jsonPath("$[0].textContent").value("İkinci sürüm"));
        mockMvc.perform(as(get("/api/assignments/submissions/{id}/versions", submissionId), TestTokens.student(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/assignments/files/download").param("url", firstFile), TestTokens.academician(assistant)))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/assignments/files/download").param("url", firstFile), TestTokens.student(student)))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(delete(path, assignmentId, classmate), TestTokens.academician(instructor))).andExpect(status().isNoContent());
        assertThat(submissionRepository.findById(UUID.fromString(submissionId)).orElseThrow().isLate()).isTrue();
    }

    @Test
    void theLateDeadlineMustFollowTheDueDate() throws Exception {
        String json = "{\"title\":\"Hatalı\",\"dueDate\":\"" + LocalDateTime.now().plusDays(3).withNano(0) + "\",\"lateUntil\":\""
                + LocalDateTime.now().plusDays(2).withNano(0) + "\",\"courseId\":\"" + courseId + "\"}";
        MockMultipartFile part = new MockMultipartFile("assignment", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(as(multipart("/api/assignments").file(part), TestTokens.academician(instructor)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_LATE_UNTIL"));
        mockMvc.perform(json(put("/api/assignments/{id}", assignmentId), TestTokens.academician(instructor),
                        "{\"clearLateUntil\":true,\"latePenaltyPercent\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lateUntil").doesNotExist());
        submit(student, null, "Artık kapalı").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SUBMISSION_CLOSED"));
    }

    private ResultActions submit(UUID studentId, MockMultipartFile file, String text) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/assignments/{id}/submit", assignmentId);
        if (file != null) {
            request.file(file);
        }
        if (text != null) {
            request.param("text", text);
        }
        return mockMvc.perform(as(request, TestTokens.student(studentId)));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
