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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AssignmentIntegrationTest
class AssignmentNotificationTest {

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructor = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID classmate = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AssignmentRepository assignmentRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID assignmentId;

    @BeforeEach
    void assignment() {
        FakeCourseService.course(courseId, instructor, student, classmate);
        Assignment assignment = new Assignment();
        assignment.setTitle("Bildirimli Ödev");
        assignment.setCourseId(courseId);
        assignment.setDueDate(LocalDateTime.now().plusDays(7).withNano(0));
        assignmentId = assignmentRepository.save(assignment).getId();
    }

    @Test
    void deadlineChangesReachEveryEnrolledStudentButOtherEditsDoNot() throws Exception {
        update("{\"title\":\"Bildirimli Ödev\",\"description\":\"Yeni açıklama\"}").andExpect(status().isOk());
        assertThat(notifications("ASSIGNMENT_DEADLINE_CHANGED")).isEmpty();

        String due = LocalDateTime.now().plusDays(10).withHour(23).withMinute(59).withSecond(0).withNano(0)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        update("{\"dueDate\":\"" + due + "\"}").andExpect(status().isOk());

        assertThat(notifications("ASSIGNMENT_DEADLINE_CHANGED")).singleElement().asString()
                .contains(student.toString(), classmate.toString(), "\"category\":\"COURSE\"", "[AUTH-101] Teslim tarihi değişti",
                        "23:59", "\"link\":\"/courses/" + courseId + "/assignments/" + assignmentId + "\"");
    }

    @Test
    void publishedGradesAndLaterChangesReachOnlyTheSubmitters() throws Exception {
        UUID submission = submissionRepository.save(new AssignmentSubmission(assignmentId, student, null, false)).getId();
        grade(submission, "{\"grade\":70}");
        assertThat(notifications("ASSIGNMENT_GRADE_CHANGED")).isEmpty();

        mockMvc.perform(as(post("/api/assignments/{id}/publish-grades", assignmentId), TestTokens.academician(instructor)))
                .andExpect(status().isOk());
        assertThat(notifications("ASSIGNMENT_GRADES_PUBLISHED")).singleElement().asString()
                .contains(student.toString(), "Puanlar ilan edildi").doesNotContain(classmate.toString());

        grade(submission, "{\"grade\":75,\"reason\":\"Toplama hatası\"}");
        assertThat(notifications("ASSIGNMENT_GRADE_CHANGED")).singleElement().asString()
                .contains(student.toString(), "Puanınız güncellendi", "Toplama hatası");
    }

    @Test
    void extensionsAreAnnouncedToTheStudentTheyConcern() throws Exception {
        String until = LocalDateTime.now().plusDays(12).withHour(17).withMinute(0).withSecond(0).withNano(0)
                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        mockMvc.perform(as(put("/api/assignments/{id}/extensions/{student}", assignmentId, student), TestTokens.academician(instructor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dueDate\":\"" + until + "\",\"reason\":\"Sağlık raporu\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(as(delete("/api/assignments/{id}/extensions/{student}", assignmentId, student),
                        TestTokens.academician(instructor)))
                .andExpect(status().is2xxSuccessful());

        assertThat(notifications("ASSIGNMENT_EXTENSION")).hasSize(2)
                .allSatisfy(body -> assertThat(body).contains(student.toString()).doesNotContain(classmate.toString()))
                .anySatisfy(body -> assertThat(body).contains("Süre uzatımı: Bildirimli Ödev", "17:00", "Sağlık raporu"))
                .anySatisfy(body -> assertThat(body).contains("Süre uzatımı kaldırıldı"));
    }

    private ResultActions update(String body) throws Exception {
        return mockMvc.perform(as(put("/api/assignments/{id}", assignmentId), TestTokens.academician(instructor))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void grade(UUID submission, String body) throws Exception {
        mockMvc.perform(as(put("/api/assignments/submissions/{id}/grade", submission), TestTokens.academician(instructor))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private List<String> notifications(String type) {
        return jdbcTemplate.queryForList("select convert_from(body, 'UTF8') from assignment_db.outbox_messages "
                        + "where routing_key = 'notification.request' and convert_from(body, 'UTF8') like ? "
                        + "and convert_from(body, 'UTF8') like ?",
                String.class, "%" + assignmentId + "%", "%\"type\":\"" + type + "\"%");
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
