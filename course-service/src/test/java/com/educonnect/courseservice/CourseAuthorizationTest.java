package com.educonnect.courseservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.repository.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CourseIntegrationTest
class CourseAuthorizationTest {

	private final UUID owner = UUID.randomUUID();
	private final UUID otherInstructor = UUID.randomUUID();
	private final UUID student = UUID.randomUUID();
	private final UUID admin = UUID.randomUUID();

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private CourseRepository courseRepository;

	private UUID courseId;

	@BeforeEach
	void createCourse() {
		Course course = new Course();
		course.setTitle("Yetki Testi");
		course.setCode("AUTH-" + UUID.randomUUID().toString().substring(0, 8));
		course.setCredit(3);
		course.setCapacity(10);
		course.setInstructorId(owner);
		courseId = courseRepository.save(course).getId();
	}

	@Test
	void onlyAcademiciansCreateCoursesAndOnlyForThemselves() throws Exception {
		mockMvc.perform(as(multipart("/api/courses").file(coursePart(student)), TestTokens.student(student)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED_COURSE_ACCESS"));
		mockMvc.perform(as(multipart("/api/courses").file(coursePart(owner)), TestTokens.academician(otherInstructor)))
				.andExpect(status().isForbidden());
	}

	@Test
	void onlyTheOwnerOrAnAdminDeletesACourse() throws Exception {
		mockMvc.perform(as(delete("/api/courses/{id}", courseId), TestTokens.student(student)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(delete("/api/courses/{id}", courseId), TestTokens.academician(otherInstructor)))
				.andExpect(status().isForbidden());
		assertThat(courseRepository.existsById(courseId)).isTrue();

		mockMvc.perform(as(delete("/api/courses/{id}", courseId), TestTokens.academician(owner)))
				.andExpect(status().isNoContent());
		assertThat(courseRepository.existsById(courseId)).isFalse();
	}

	@Test
	void adminDeletesAnyCourse() throws Exception {
		mockMvc.perform(as(delete("/api/courses/{id}", courseId), TestTokens.admin(admin)))
				.andExpect(status().isNoContent());
	}

	@Test
	void onlyStudentsApplyToCourses() throws Exception {
		mockMvc.perform(as(post("/api/courses/{id}/apply", courseId), TestTokens.academician(otherInstructor)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(post("/api/courses/{id}/apply", courseId), TestTokens.student(student)))
				.andExpect(status().isCreated());
	}

	@Test
	void courseDataIsVisibleOnlyToItsInstructor() throws Exception {
		mockMvc.perform(as(get("/api/courses/{id}/applications/pending", courseId), TestTokens.academician(otherInstructor)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/courses/{id}/enrolled-students", courseId), TestTokens.academician(otherInstructor)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/courses/{id}/enrolled-students", courseId), TestTokens.student(student)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get("/api/courses/{id}/applications/pending", courseId), TestTokens.academician(owner)))
				.andExpect(status().isOk());
	}

	@Test
	void onlyTheInstructorPostsAnnouncements() throws Exception {
		String body = "{\"title\":\"Sınav tarihi\",\"content\":\"Vize 10 Kasım\"}";
		mockMvc.perform(as(post("/api/courses/{id}/announcements", courseId), TestTokens.academician(otherInstructor))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(post("/api/courses/{id}/announcements", courseId), TestTokens.student(student))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(post("/api/courses/{id}/announcements", courseId), TestTokens.academician(owner))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
	}

	@Test
	void internalEndpointsAcceptOnlyServiceTokens() throws Exception {
		String path = "/api/courses/internal/instructors/{id}/course-ids";
		mockMvc.perform(get(path, owner))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(as(get(path, owner), TestTokens.admin(admin)))
				.andExpect(status().isForbidden());
		mockMvc.perform(as(get(path, owner), TestTokens.service("assignment-service")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0]").value(courseId.toString()));
	}

	private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
		return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
	}

	private static MockMultipartFile coursePart(UUID instructorId) {
		String json = "{\"title\":\"Yeni Ders\",\"code\":\"NEW-" + UUID.randomUUID().toString().substring(0, 6)
				+ "\",\"credit\":3,\"capacity\":10,\"instructorId\":\"" + instructorId + "\"}";
		return new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
	}
}
