package com.educonnect.assignmentservice;

import com.educonnect.common.test.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AssignmentIntegrationTest
class GroupSetTest {

    private final UUID courseId = UUID.randomUUID();
    private final UUID instructor = UUID.randomUUID();
    private final UUID assistant = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID classmate = UUID.randomUUID();
    private final UUID third = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void course() {
        FakeCourseService.course(courseId, instructor, student, classmate, third);
        FakeCourseService.staff(courseId, assistant, "ASSISTANT");
    }

    @Test
    void teachersBuildGroupsAndStudentsSeeOnlyTheirOwnMembers() throws Exception {
        String setId = createSet("Proje grupları", true, 2);
        createSetRequest(TestTokens.academician(instructor), "{\"name\":\"proje GRUPLARI\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("GROUP_SET_EXISTS"));
        createSetRequest(TestTokens.academician(assistant), "{\"name\":\"Asistan seti\"}").andExpect(status().isForbidden());
        createSetRequest(TestTokens.student(student), "{\"name\":\"Öğrenci seti\"}").andExpect(status().isForbidden());

        String groupA = createGroup(setId, "Grup A");
        String groupB = createGroup(setId, "Grup B");
        json(post("/api/assignments/group-sets/{id}/groups", setId), TestTokens.academician(instructor), "{\"name\":\"grup a\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("GROUP_EXISTS"));

        member(groupA, student).andExpect(status().isOk());
        member(groupA, outsider).andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("STUDENT_NOT_ENROLLED"));
        member(groupA, classmate).andExpect(status().isOk());
        member(groupA, third).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("GROUP_FULL"));
        member(groupB, student).andExpect(status().isOk());

        mockMvc.perform(as(get("/api/assignments/course/{id}/group-sets", courseId), TestTokens.student(classmate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].myGroupId").value(groupA))
                .andExpect(jsonPath("$[0].signupOpen").value(true))
                .andExpect(jsonPath("$[0].groups[?(@.id == '" + groupA + "')].members[*].studentId").value(classmate.toString()))
                .andExpect(jsonPath("$[0].groups[?(@.id == '" + groupB + "')].memberCount").value(1))
                .andExpect(jsonPath("$[0].groups[?(@.id == '" + groupB + "')].members", contains(hasSize(0))));
        mockMvc.perform(as(get("/api/assignments/course/{id}/group-sets", courseId), TestTokens.academician(assistant)))
                .andExpect(jsonPath("$[0].groups[?(@.id == '" + groupB + "')].members[*].studentId").value(student.toString()));
        mockMvc.perform(as(get("/api/assignments/course/{id}/group-sets", courseId), TestTokens.student(outsider)))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(delete("/api/assignments/groups/{g}/members/{s}", groupB, student), TestTokens.academician(instructor)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(delete("/api/assignments/groups/{g}", groupB), TestTokens.academician(instructor)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(delete("/api/assignments/group-sets/{id}", setId), TestTokens.academician(instructor)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(get("/api/assignments/course/{id}/group-sets", courseId), TestTokens.academician(instructor)))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void studentsJoinAndLeaveGroupsWhileSignupIsOpen() throws Exception {
        String setId = createSet("Laboratuvar", true, 2);
        String groupA = createGroup(setId, "Masa 1");
        String groupB = createGroup(setId, "Masa 2");

        join(groupA, student).andExpect(status().isOk()).andExpect(jsonPath("$[0].myGroupId").value(groupA));
        join(groupB, student).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ALREADY_IN_GROUP"));
        join(groupA, classmate).andExpect(status().isOk());
        join(groupA, third).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("GROUP_FULL"));
        mockMvc.perform(as(post("/api/assignments/groups/{g}/leave", groupA), TestTokens.student(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].myGroupId").doesNotExist());
        join(groupA, third).andExpect(status().isOk());

        String closed = createSet("Hoca seçer", false, null);
        String closedGroup = createGroup(closed, "Tek");
        join(closedGroup, student).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("SIGNUP_CLOSED"));

        json(put("/api/assignments/group-sets/{id}", setId), TestTokens.academician(instructor),
                "{\"name\":\"Laboratuvar\",\"selfSignup\":true,\"maxMembers\":2,\"signupClosesAt\":\""
                        + LocalDateTime.now().minusMinutes(1).withNano(0) + "\"}")
                .andExpect(status().isOk());
        join(groupB, student).andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("SIGNUP_CLOSED"));
    }

    private String createSet(String name, boolean selfSignup, Integer maxMembers) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"selfSignup\":" + selfSignup
                + (maxMembers != null ? ",\"maxMembers\":" + maxMembers : "") + "}";
        String response = createSetRequest(TestTokens.academician(instructor), body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(response, "$[?(@.name == '" + name + "')].id").toString().replaceAll("[\\[\\]\"]", "");
    }

    private ResultActions createSetRequest(String token, String body) throws Exception {
        return json(post("/api/assignments/course/{id}/group-sets", courseId), token, body);
    }

    private String createGroup(String setId, String name) throws Exception {
        String response = json(post("/api/assignments/group-sets/{id}/groups", setId), TestTokens.academician(instructor),
                "{\"name\":\"" + name + "\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(response, "$[*].groups[?(@.name == '" + name + "')].id").toString().replaceAll("[\\[\\]\"]", "");
    }

    private ResultActions member(String groupId, UUID studentId) throws Exception {
        return mockMvc.perform(as(put("/api/assignments/groups/{g}/members/{s}", groupId, studentId), TestTokens.academician(instructor)));
    }

    private ResultActions join(String groupId, UUID studentId) throws Exception {
        return mockMvc.perform(as(post("/api/assignments/groups/{g}/join", groupId), TestTokens.student(studentId)));
    }

    private ResultActions json(AbstractMockHttpServletRequestBuilder<?> request, String token, String body) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
