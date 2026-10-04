package com.educonnect.assignmentservice;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AssignmentIntegrationTest
class AiPolicyTest {

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructor = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID classmate = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void course() {
        FakeCourseService.course(courseId, instructor, student, classmate);
    }

    @Test
    void instructorsPickAPolicyPerAssignmentAndGuidanceIsTheDefault() throws Exception {
        String byDefault = id(create("Varsayılan", "").andExpect(status().isOk()).andExpect(jsonPath("$.aiPolicy").value("GUIDANCE")));
        create("Yasak", "\"aiPolicy\":\"NONE\"").andExpect(status().isOk()).andExpect(jsonPath("$.aiPolicy").value("NONE"));
        create("Bozuk", "\"aiPolicy\":\"ANYTHING\"").andExpect(status().isBadRequest());

        mockMvc.perform(json(put("/api/assignments/{id}", byDefault), TestTokens.academician(instructor),
                        "{\"aiPolicy\":\"ALLOWED_WITH_DISCLOSURE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiPolicy").value("ALLOWED_WITH_DISCLOSURE"));
        mockMvc.perform(as(get("/api/assignments/{id}/changes", byDefault), TestTokens.academician(instructor)))
                .andExpect(jsonPath("$[0].field").value("aiPolicy"))
                .andExpect(jsonPath("$[0].oldValue").value("GUIDANCE"))
                .andExpect(jsonPath("$[0].newValue").value("ALLOWED_WITH_DISCLOSURE"));
        mockMvc.perform(as(get("/api/assignments/my-assignments"), TestTokens.student(student)))
                .andExpect(jsonPath("$[?(@.id == '" + byDefault + "')].aiPolicy").value("ALLOWED_WITH_DISCLOSURE"));
    }

    @Test
    void disclosurePolicyRequiresADeclarationThatTheInstructorSees() throws Exception {
        String assignment = id(create("Beyanlı", "\"aiPolicy\":\"ALLOWED_WITH_DISCLOSURE\"").andExpect(status().isOk()));

        submit(assignment, student, null, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("AI_DECLARATION_REQUIRED"));
        submit(assignment, student, "true", "Taslağın dilini düzeltmek için kullandım")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aiUsed").value(true))
                .andExpect(jsonPath("$.aiNote").value("Taslağın dilini düzeltmek için kullandım"));
        submit(assignment, classmate, "false", "yok sayılır")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aiUsed").value(false))
                .andExpect(jsonPath("$.aiNote").doesNotExist());

        mockMvc.perform(as(get("/api/assignments/{id}/submissions", assignment), TestTokens.academician(instructor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.studentId == '" + student + "')].aiUsed").value(true))
                .andExpect(jsonPath("$[?(@.studentId == '" + student + "')].aiNote").value("Taslağın dilini düzeltmek için kullandım"));
        mockMvc.perform(as(get("/api/assignments/my-assignments"), TestTokens.student(student)))
                .andExpect(jsonPath("$[?(@.id == '" + assignment + "')].submission.aiUsed").value(true));

        String guided = id(create("Rehberli", "").andExpect(status().isOk()));
        submit(guided, student, null, null).andExpect(status().isCreated()).andExpect(jsonPath("$.aiUsed").doesNotExist());
    }

    private ResultActions submit(String assignment, UUID who, String aiUsed, String aiNote) throws Exception {
        var request = multipart("/api/assignments/{id}/submit", assignment)
                .file(new MockMultipartFile("file", "cevap.txt", MediaType.TEXT_PLAIN_VALUE, "cevap".getBytes(StandardCharsets.UTF_8)));
        if (aiUsed != null) {
            request.param("aiUsed", aiUsed);
        }
        if (aiNote != null) {
            request.param("aiNote", aiNote);
        }
        return mockMvc.perform(as(request, TestTokens.student(who)));
    }

    private ResultActions create(String title, String extra) throws Exception {
        String json = "{\"title\":\"" + title + "\",\"dueDate\":\"" + LocalDateTime.now().plusDays(3).withNano(0)
                + "\",\"courseId\":\"" + courseId + "\"" + (extra.isEmpty() ? "" : "," + extra) + "}";
        MockMultipartFile part = new MockMultipartFile("assignment", "", MediaType.APPLICATION_JSON_VALUE,
                json.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(as(multipart("/api/assignments").file(part), TestTokens.academician(instructor)));
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
