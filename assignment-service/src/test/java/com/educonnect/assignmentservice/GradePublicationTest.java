package com.educonnect.assignmentservice;

import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.common.test.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AssignmentIntegrationTest
class GradePublicationTest {

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructor = UUID.randomUUID();
    private final UUID assistant = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID classmate = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    private UUID assignmentId;
    private UUID submissionId;

    @BeforeEach
    void gradedSubmission() {
        FakeCourseService.course(courseId, instructor, student, classmate);
        FakeCourseService.staff(courseId, assistant, "ASSISTANT");
        Assignment assignment = new Assignment();
        assignment.setTitle("İlan Ödevi");
        assignment.setCourseId(courseId);
        assignment.setDueDate(LocalDateTime.now().plusDays(7));
        assignmentId = assignmentRepository.save(assignment).getId();
        submissionId = submissionRepository.save(new AssignmentSubmission(assignmentId, student, null, false)).getId();
    }

    @Test
    void studentsSeeGradesOnlyAfterTheyArePublished() throws Exception {
        grade(submissionId, TestTokens.academician(assistant), "{\"grade\":80,\"feedback\":\"İyi\"}").andExpect(status().isOk());
        myAssignment(student)
                .andExpect(jsonPath("$[0].gradesPublished").value(false))
                .andExpect(jsonPath("$[0].submission.grade").doesNotExist())
                .andExpect(jsonPath("$[0].submission.feedback").doesNotExist());

        publish(TestTokens.academician(assistant)).andExpect(status().isForbidden());
        publish(TestTokens.student(student)).andExpect(status().isForbidden());
        publish(TestTokens.academician(instructor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gradesPublishedAt", notNullValue()));
        publish(TestTokens.academician(instructor))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("GRADES_ALREADY_PUBLISHED"));

        myAssignment(student)
                .andExpect(jsonPath("$[0].gradesPublished").value(true))
                .andExpect(jsonPath("$[0].submission.grade").value(80))
                .andExpect(jsonPath("$[0].submission.feedback").value("İyi"));
    }

    @Test
    void regradingWithoutFeedbackKeepsTheEarlierFeedback() throws Exception {
        grade(submissionId, TestTokens.academician(assistant), "{\"grade\":70,\"feedback\":\"Kaynakça eksik\"}")
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/assignments/{id}/submissions", assignmentId), TestTokens.academician(assistant)))
                .andExpect(jsonPath("$[0].feedback").value("Kaynakça eksik"));

        grade(submissionId, TestTokens.academician(assistant), "{\"grade\":75}").andExpect(status().isOk());
        mockMvc.perform(as(get("/api/assignments/{id}/submissions", assignmentId), TestTokens.academician(assistant)))
                .andExpect(jsonPath("$[0].grade").value(75.0))
                .andExpect(jsonPath("$[0].feedback").value("Kaynakça eksik"));

        grade(submissionId, TestTokens.academician(assistant), "{\"grade\":75,\"feedback\":\"\"}").andExpect(status().isOk());
        mockMvc.perform(as(get("/api/assignments/{id}/submissions", assignmentId), TestTokens.academician(assistant)))
                .andExpect(jsonPath("$[0].feedback").doesNotExist());
    }

    @Test
    void changingAPublishedGradeNeedsAReasonAndIsRecorded() throws Exception {
        grade(submissionId, TestTokens.academician(instructor), "{\"grade\":70}").andExpect(status().isOk());
        publish(TestTokens.academician(instructor)).andExpect(status().isOk());

        grade(submissionId, TestTokens.academician(instructor), "{\"grade\":75}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("GRADE_CHANGE_REASON_REQUIRED"));
        grade(submissionId, TestTokens.academician(instructor), "{\"grade\":75,\"reason\":\"Toplama hatası\"}")
                .andExpect(status().isOk());
        UUID lateSubmission = submissionRepository.save(new AssignmentSubmission(assignmentId, classmate, null, true)).getId();
        grade(lateSubmission, TestTokens.academician(instructor), "{\"grade\":60}").andExpect(status().isOk());

        mockMvc.perform(as(get("/api/assignments/submissions/{id}/grade-history", submissionId), TestTokens.academician(assistant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].oldGrade").value(70))
                .andExpect(jsonPath("$[0].newGrade").value(75))
                .andExpect(jsonPath("$[0].afterPublication").value(true))
                .andExpect(jsonPath("$[0].reason").value("Toplama hatası"))
                .andExpect(jsonPath("$[0].changedBy").value(instructor.toString()))
                .andExpect(jsonPath("$[1].afterPublication").value(false));
        mockMvc.perform(as(get("/api/assignments/submissions/{id}/grade-history", submissionId), TestTokens.student(student)))
                .andExpect(status().isForbidden());
        myAssignment(student).andExpect(jsonPath("$[0].submission.grade").value(75));
    }

    private ResultActions grade(UUID submission, String token, String body) throws Exception {
        return mockMvc.perform(as(put("/api/assignments/submissions/{id}/grade", submission), token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions publish(String token) throws Exception {
        return mockMvc.perform(as(post("/api/assignments/{id}/publish-grades", assignmentId), token));
    }

    private ResultActions myAssignment(UUID user) throws Exception {
        return mockMvc.perform(as(get("/api/assignments/my-assignments"), TestTokens.student(user)))
                .andExpect(status().isOk());
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
