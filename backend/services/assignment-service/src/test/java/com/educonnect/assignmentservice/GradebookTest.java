package com.educonnect.assignmentservice;

import com.educonnect.assignmentservice.model.AssessmentType;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.common.test.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AssignmentIntegrationTest
class GradebookTest {

    private static final String BYTE_ORDER_MARK = String.valueOf((char) 0xFEFF);

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructor = UUID.randomUUID();
    private final UUID assistant = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID classmate = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    private Assignment midterm;
    private Assignment fin;
    private Assignment practice;

    @BeforeEach
    void course() {
        FakeCourseService.course(courseId, instructor, student, classmate);
        FakeCourseService.staff(courseId, assistant, "ASSISTANT");
        midterm = assessment("Vize", AssessmentType.MIDTERM, "40", "50", 10);
        fin = assessment("Final", AssessmentType.FINAL, "60", "100", 40);
        practice = assessment("Alıştırma", AssessmentType.HOMEWORK, "0", "100", 20);
        midterm.publishGrades(instructor, Instant.now());
        midterm = assignmentRepository.save(midterm);
        submit(midterm, student, "40");
        submit(fin, student, "75");
        submit(midterm, classmate, null);
    }

    @Test
    void theGradebookListsEveryEnrolledStudentWithMissingWorkAndWeightedTotals() throws Exception {
        mockMvc.perform(as(get("/api/assignments/course/{id}/gradebook", courseId), TestTokens.academician(assistant)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWeight").value(100.0))
                .andExpect(jsonPath("$.assessments[*].title", contains("Vize", "Alıştırma", "Final")))
                .andExpect(jsonPath("$.students", hasSize(2)))
                .andExpect(jsonPath("$.students[?(@.studentId == '" + student + "')].weightedTotal").value(77.0))
                .andExpect(jsonPath("$.students[?(@.studentId == '" + student + "')].gradedWeight").value(100.0))
                .andExpect(jsonPath("$.students[?(@.studentId == '" + student + "')].missing").value(1))
                .andExpect(jsonPath("$.students[?(@.studentId == '" + classmate + "')].weightedTotal").value(0.0))
                .andExpect(jsonPath("$.students[?(@.studentId == '" + classmate + "')].missing").value(2))
                .andExpect(jsonPath("$.students[?(@.studentId == '" + classmate + "')].grades[*].status",
                        contains("SUBMITTED", "NOT_SUBMITTED", "NOT_SUBMITTED")));
        mockMvc.perform(as(get("/api/assignments/course/{id}/gradebook", courseId), TestTokens.student(student)))
                .andExpect(status().isForbidden());
    }

    @Test
    void theGradebookExportsAsCsvForTheStudentInformationSystem() throws Exception {
        byte[] body = mockMvc.perform(as(get("/api/assignments/course/{id}/gradebook.csv", courseId), TestTokens.academician(instructor)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith("text/csv")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        String csv = new String(body, StandardCharsets.UTF_8);

        assertThat(csv).startsWith(BYTE_ORDER_MARK + "Öğrenci No,Ad Soyad,\"Vize (%40, 50 puan)\",\"Alıştırma (%0, 100 puan)\",\"Final (%60, 100 puan)\",Ağırlıklı Toplam\r\n");
        assertThat(csv).contains(",40,-,75,77\r\n").contains(",,-,-,0\r\n");
    }

    @Test
    void studentsSeeOnlyTheirPublishedGrades() throws Exception {
        mockMvc.perform(as(get("/api/assignments/course/{id}/my-grades", courseId), TestTokens.student(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grades[*].status", contains("GRADED", "NOT_SUBMITTED", "SUBMITTED")))
                .andExpect(jsonPath("$.grades[0].grade").value(40.0))
                .andExpect(jsonPath("$.grades[2].grade").doesNotExist())
                .andExpect(jsonPath("$.weightedTotal").value(32.0))
                .andExpect(jsonPath("$.gradedWeight").value(40.0));
        mockMvc.perform(as(get("/api/assignments/course/{id}/my-grades", courseId), TestTokens.student(outsider)))
                .andExpect(status().isForbidden());
    }

    private Assignment assessment(String title, AssessmentType type, String weight, String maxPoints, int dueInDays) {
        Assignment assignment = new Assignment();
        assignment.setTitle(title);
        assignment.setCourseId(courseId);
        assignment.setType(type);
        assignment.setWeight(new BigDecimal(weight));
        assignment.setMaxPoints(new BigDecimal(maxPoints));
        assignment.setDueDate(LocalDateTime.now().plusDays(dueInDays));
        return assignmentRepository.save(assignment);
    }

    private void submit(Assignment assignment, UUID studentId, String grade) {
        AssignmentSubmission submission = new AssignmentSubmission(assignment.getId(), studentId, null, false);
        if (grade != null) {
            submission.setGrade(new BigDecimal(grade));
        }
        submissionRepository.save(submission);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
