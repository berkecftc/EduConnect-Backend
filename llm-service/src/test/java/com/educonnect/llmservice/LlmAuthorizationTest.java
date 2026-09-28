package com.educonnect.llmservice;

import com.educonnect.common.test.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@LlmIntegrationTest
class LlmAuthorizationTest {

    private static final String STUDENT_ASSISTANT = "/api/ai/student-assistant";
    private static final String INSTRUCTOR_COPILOT = "/api/ai/instructor-copilot";
    private static final String MESSAGE = "{\"message\":\"Merhaba\"}";
    private static final String BLANK_MESSAGE = "{\"message\":\" \"}";

    private final UUID student = UUID.randomUUID();
    private final UUID academician = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void studentAssistantIsOnlyForStudents() throws Exception {
        mockMvc.perform(json(post(STUDENT_ASSISTANT), MESSAGE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        mockMvc.perform(as(json(post(STUDENT_ASSISTANT), MESSAGE), TestTokens.academician(academician)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(as(json(post(STUDENT_ASSISTANT), MESSAGE), TestTokens.admin(admin)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(json(post(STUDENT_ASSISTANT), MESSAGE), TestTokens.user(admin, "ROLE_CLUB_OFFICIAL")))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(json(post(STUDENT_ASSISTANT), BLANK_MESSAGE), TestTokens.student(student)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void instructorCopilotIsOnlyForAcademicians() throws Exception {
        mockMvc.perform(json(post(INSTRUCTOR_COPILOT), MESSAGE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        mockMvc.perform(as(json(post(INSTRUCTOR_COPILOT), MESSAGE), TestTokens.student(student)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(as(json(post(INSTRUCTOR_COPILOT), MESSAGE), TestTokens.admin(admin)))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(json(post(INSTRUCTOR_COPILOT), BLANK_MESSAGE), TestTokens.academician(academician)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    void spoofedIdentityHeadersAndServiceTokensDoNotReachTheAssistants() throws Exception {
        mockMvc.perform(json(post(STUDENT_ASSISTANT), MESSAGE)
                        .header("X-Authenticated-User-Id", student.toString())
                        .header("X-Authenticated-User-Roles", "ROLE_STUDENT"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(json(post(INSTRUCTOR_COPILOT), MESSAGE), TestTokens.service("course-service")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(json(post(STUDENT_ASSISTANT), MESSAGE).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void internalNamespaceRejectsUserTokens() throws Exception {
        mockMvc.perform(get("/api/ai/internal/anything"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(get("/api/ai/internal/anything"), TestTokens.admin(admin)))
                .andExpect(status().isForbidden());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
