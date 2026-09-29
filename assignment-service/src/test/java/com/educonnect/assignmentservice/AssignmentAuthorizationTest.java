package com.educonnect.assignmentservice;

import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
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
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AssignmentIntegrationTest
class AssignmentAuthorizationTest {

	private final UUID courseId = UUID.randomUUID();
	private final UUID instructor = UUID.randomUUID();
	private final UUID otherInstructor = UUID.randomUUID();
	private final UUID student = UUID.randomUUID();
	private final UUID classmate = UUID.randomUUID();
	private final UUID outsider = UUID.randomUUID();
	private final UUID admin = UUID.randomUUID();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AssignmentRepository assignmentRepository;

	@Autowired
	private SubmissionRepository submissionRepository;

	private UUID assignmentId;

	@BeforeEach
	void createCourseAndAssignment() {
		FakeCourseService.course(courseId, instructor, student, classmate);
		assignmentId = assignmentRepository.save(assignment(courseId)).getId();
	}

	@Test
	void onlyTheCourseInstructorCreatesAssignments() throws Exception {
		mockMvc.perform(as(multipart("/api/assignments").file(assignmentPart(courseId)), TestTokens.student(student)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
		mockMvc.perform(as(multipart("/api/assignments").file(assignmentPart(courseId)), TestTokens.academician(otherInstructor)))
				.andExpect(status().isForbidden());
		assertThat(assignmentRepository.findByCourseId(courseId)).hasSize(1);

		mockMvc.perform(as(multipart("/api/assignments").file(assignmentPart(courseId)), TestTokens.academician(instructor)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.courseId").value(courseId.toString()));
		assertThat(assignmentRepository.findByCourseId(courseId)).hasSize(2);
	}

	@Test
	void adminBypassesTheInstructorCheck() throws Exception {
		mockMvc.perform(as(multipart("/api/assignments").file(assignmentPart(courseId)), TestTokens.admin(admin)))
				.andExpect(status().isOk());
	}

	@Test
	void assignmentListIsVisibleOnlyToCourseMembers() throws Exception {
		mockMvc.perform(as(get("/api/assignments/course/{courseId}", courseId), TestTokens.student(outsider)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/assignments/course/{courseId}", courseId), TestTokens.academician(otherInstructor)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/assignments/course/{courseId}", courseId), TestTokens.student(student)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(assignmentId.toString()));
		mockMvc.perform(as(get("/api/assignments/course/{courseId}", courseId), TestTokens.academician(instructor)))
				.andExpect(status().isOk());
	}

	@Test
	void onlyTheCourseInstructorDeletesAnAssignment() throws Exception {
		mockMvc.perform(as(delete("/api/assignments/{id}", assignmentId), TestTokens.student(student)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(delete("/api/assignments/{id}", assignmentId), TestTokens.academician(otherInstructor)))
				.andExpect(status().isForbidden());
		assertThat(assignmentRepository.existsById(assignmentId)).isTrue();

		mockMvc.perform(as(delete("/api/assignments/{id}", assignmentId), TestTokens.academician(instructor)))
				.andExpect(status().isNoContent());
		assertThat(assignmentRepository.existsById(assignmentId)).isFalse();
	}

	@Test
	void onlyEnrolledStudentsSubmit() throws Exception {
		mockMvc.perform(as(multipart("/api/assignments/{id}/submit", assignmentId).file(submissionFile()), TestTokens.student(outsider)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(multipart("/api/assignments/{id}/submit", assignmentId).file(submissionFile()), TestTokens.academician(instructor)))
				.andExpect(status().isForbidden());
		assertThat(submissionRepository.findByAssignmentId(assignmentId)).isEmpty();

		mockMvc.perform(as(multipart("/api/assignments/{id}/submit", assignmentId).file(submissionFile()), TestTokens.student(student)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.studentId").value(student.toString()));
	}

	@Test
	void onlyTheCourseInstructorGradesASubmission() throws Exception {
		UUID submissionId = submissionRepository.save(new AssignmentSubmission(assignmentId, student, null, false)).getId();
		String body = "{\"grade\":100,\"feedback\":\"Harika\"}";

		mockMvc.perform(as(put("/api/assignments/submissions/{id}/grade", submissionId), TestTokens.student(student))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(put("/api/assignments/submissions/{id}/grade", submissionId), TestTokens.academician(otherInstructor))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
		assertThat(submissionRepository.findById(submissionId).orElseThrow().getGrade()).isNull();

		mockMvc.perform(as(put("/api/assignments/submissions/{id}/grade", submissionId), TestTokens.academician(instructor))
						.contentType(MediaType.APPLICATION_JSON).content("{\"grade\":85,\"feedback\":\"İyi\"}"))
				.andExpect(status().isOk());
		assertThat(submissionRepository.findById(submissionId).orElseThrow().getGrade()).isEqualTo(85);
	}

	@Test
	void submissionListsAreVisibleOnlyToTheCourseInstructor() throws Exception {
		submissionRepository.save(new AssignmentSubmission(assignmentId, student, null, false));

		for (String path : new String[]{"/api/assignments/course/" + courseId + "/submissions",
				"/api/assignments/" + assignmentId + "/submissions"}) {
			mockMvc.perform(as(get(path), TestTokens.student(student)))
					.andExpect(status().isForbidden());
			mockMvc.perform(as(get(path), TestTokens.academician(otherInstructor)))
					.andExpect(status().isForbidden());
			mockMvc.perform(as(get(path), TestTokens.academician(instructor)))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[0].studentId").value(student.toString()));
		}
	}

	@Test
	void aSubmissionFileIsDownloadableOnlyByItsOwnerAndTheInstructor() throws Exception {
		String response = mockMvc.perform(as(multipart("/api/assignments/{id}/submit", assignmentId).file(submissionFile()),
						TestTokens.student(student)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		String fileUrl = JsonPath.read(response, "$.submissionFileUrl");

		mockMvc.perform(as(get("/api/assignments/files/download").param("url", fileUrl), TestTokens.student(classmate)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/assignments/files/download").param("url", fileUrl), TestTokens.student(outsider)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/assignments/files/download").param("url", fileUrl), TestTokens.academician(otherInstructor)))
				.andExpect(status().isForbidden());

		mockMvc.perform(as(get("/api/assignments/files/download").param("url", fileUrl), TestTokens.student(student)))
				.andExpect(status().isOk())
				.andExpect(content().string("cevap"));
		mockMvc.perform(as(get("/api/assignments/files/download").param("url", fileUrl), TestTokens.academician(instructor)))
				.andExpect(status().isOk());
	}

	@Test
	void anAssignmentAttachmentIsDownloadableOnlyByCourseMembers() throws Exception {
		MockMultipartFile attachment = new MockMultipartFile("file", "odev.pdf", MediaType.APPLICATION_PDF_VALUE,
				"soru".getBytes(StandardCharsets.UTF_8));
		String response = mockMvc.perform(as(multipart("/api/assignments").file(assignmentPart(courseId)).file(attachment),
						TestTokens.academician(instructor)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		String fileUrl = JsonPath.read(response, "$.fileUrl");

		mockMvc.perform(as(get("/api/assignments/files/download").param("url", fileUrl), TestTokens.student(outsider)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/assignments/files/download").param("url", fileUrl), TestTokens.academician(otherInstructor)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/assignments/files/download").param("url", fileUrl), TestTokens.student(classmate)))
				.andExpect(status().isOk())
				.andExpect(content().string("soru"));
	}

	@Test
	void downloadingAnUnknownFileIsNotFound() throws Exception {
		mockMvc.perform(as(get("/api/assignments/files/download").param("url", "baska-bir-dosya.pdf"), TestTokens.student(student)))
				.andExpect(status().isNotFound());
	}

	@Test
	void myAssignmentsListsOnlyTheCallersCourses() throws Exception {
		mockMvc.perform(as(get("/api/assignments/my-assignments"), TestTokens.student(student)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == '" + assignmentId + "')]").exists());
		mockMvc.perform(as(get("/api/assignments/my-assignments"), TestTokens.student(outsider)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.id == '" + assignmentId + "')]").doesNotExist());
	}

	@Test
	void courseChecksFailClosedWhenCourseServiceDoesNotKnowTheCourse() throws Exception {
		UUID unknownCourse = UUID.randomUUID();
		UUID orphanAssignment = assignmentRepository.save(assignment(unknownCourse)).getId();

		mockMvc.perform(as(multipart("/api/assignments").file(assignmentPart(unknownCourse)), TestTokens.academician(instructor)))
				.andExpect(status().isNotFound());
		mockMvc.perform(as(multipart("/api/assignments/{id}/submit", orphanAssignment).file(submissionFile()), TestTokens.student(student)))
				.andExpect(status().isServiceUnavailable());
		assertThat(submissionRepository.findByAssignmentId(orphanAssignment)).isEmpty();
	}

	@Test
	void requestsWithoutATokenAreRejectedAndForgedIdentityHeadersAreIgnored() throws Exception {
		mockMvc.perform(get("/api/assignments/course/{courseId}", courseId))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/assignments/course/{courseId}", courseId)
						.header("X-Authenticated-User-Id", instructor.toString())
						.header("X-Authenticated-User-Roles", "ROLE_ADMIN"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void internalPathsRequireAServiceToken() throws Exception {
		String path = "/api/assignments/internal/anything";
		mockMvc.perform(get(path))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(as(get(path), TestTokens.admin(admin)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get(path), TestTokens.service("course-service")))
				.andExpect(status().isNotFound());
	}

	private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
	}

	private static Assignment assignment(UUID courseId) {
		Assignment assignment = new Assignment();
		assignment.setTitle("Yetki Ödevi");
		assignment.setCourseId(courseId);
		assignment.setDueDate(LocalDateTime.now().plusDays(7));
		return assignment;
	}

	private static MockMultipartFile assignmentPart(UUID courseId) {
		String json = "{\"title\":\"Yeni Ödev\",\"dueDate\":\"" + LocalDateTime.now().plusDays(3).withNano(0)
				+ "\",\"courseId\":\"" + courseId + "\"}";
		return new MockMultipartFile("assignment", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
	}

	private static MockMultipartFile submissionFile() {
		return new MockMultipartFile("file", "cevap.txt", MediaType.TEXT_PLAIN_VALUE, "cevap".getBytes(StandardCharsets.UTF_8));
	}
}
