package com.educonnect.assignmentservice;

import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.model.CourseGroup;
import com.educonnect.assignmentservice.model.GroupMember;
import com.educonnect.assignmentservice.model.GroupSet;
import com.educonnect.assignmentservice.repository.CourseGroupRepository;
import com.educonnect.assignmentservice.repository.GroupMemberRepository;
import com.educonnect.assignmentservice.repository.GroupSetRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.assignmentservice.service.UserDataCleanupService;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AssignmentIntegrationTest
class GroupAssignmentTest {

    private final UUID courseId = UUID.randomUUID();
    private final UUID otherCourse = UUID.randomUUID();
    private final UUID instructor = UUID.randomUUID();
    private final UUID first = UUID.randomUUID();
    private final UUID second = UUID.randomUUID();
    private final UUID loner = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GroupSetRepository setRepository;

    @Autowired
    private CourseGroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository memberRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private UserDataCleanupService cleanupService;

    private GroupSet set;
    private CourseGroup group;
    private String assignmentId;

    @BeforeEach
    void groupAssignment() throws Exception {
        FakeCourseService.course(courseId, instructor, first, second, loner);
        GroupSet signup = new GroupSet(courseId, instructor);
        signup.setName("Proje " + UUID.randomUUID());
        signup.setSelfSignup(true);
        set = setRepository.save(signup);
        group = groupRepository.save(new CourseGroup(set.getId(), "Takım 1"));
        memberRepository.save(new GroupMember(group, first, instructor, Instant.now()));
        memberRepository.save(new GroupMember(group, second, instructor, Instant.now()));
        GroupSet foreign = new GroupSet(otherCourse, instructor);
        foreign.setName("Başka ders");
        foreign = setRepository.save(foreign);

        create("{\"groupSetId\":\"" + foreign.getId() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_GROUP_SET"));
        String created = create("{\"groupSetId\":\"" + set.getId() + "\",\"weight\":50}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupSetId").value(set.getId().toString()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assignmentId = JsonPath.read(created, "$.id");
    }

    @Test
    void oneSubmissionPerGroupSharedByItsMembers() throws Exception {
        mockMvc.perform(as(get("/api/assignments/my-assignments"), TestTokens.student(second)))
                .andExpect(jsonPath("$[0].groupName").value("Takım 1"))
                .andExpect(jsonPath("$[0].submission").doesNotExist());

        submit(loner, "Tek başıma").andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("NOT_IN_GROUP"));
        String submitted = submit(first, "Taslak").andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String submissionId = JsonPath.read(submitted, "$.id");
        submit(second, "Son hali").andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(submissionId));

        mockMvc.perform(as(get("/api/assignments/my-assignments"), TestTokens.student(second)))
                .andExpect(jsonPath("$[0].submission.submissionId").value(submissionId))
                .andExpect(jsonPath("$[0].submission.textContent").value("Son hali"));
        mockMvc.perform(as(get("/api/assignments/submissions/{id}/versions", submissionId), TestTokens.student(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].submittedBy", contains(second.toString(), first.toString())));
        mockMvc.perform(as(get("/api/assignments/submissions/{id}/versions", submissionId), TestTokens.student(loner)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/assignments/{id}/submissions", assignmentId), TestTokens.academician(instructor)))
                .andExpect(jsonPath("$[0].groupName").value("Takım 1"));

        mockMvc.perform(as(post("/api/assignments/groups/{g}/leave", group.getId()), TestTokens.student(first)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("GROUP_HAS_SUBMISSIONS"));
        mockMvc.perform(as(delete("/api/assignments/groups/{g}", group.getId()), TestTokens.academician(instructor)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("GROUP_HAS_SUBMISSIONS"));
        mockMvc.perform(as(delete("/api/assignments/group-sets/{s}", set.getId()), TestTokens.academician(instructor)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("GROUP_SET_IN_USE"));
        json(put("/api/assignments/{id}", assignmentId), "{\"clearGroupSet\":true}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("ASSIGNMENT_HAS_SUBMISSIONS"));
    }

    @Test
    void theGroupGradeAppliesToAllMembersUnlessOverridden() throws Exception {
        String submissionId = JsonPath.read(submit(first, "Proje").andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8), "$.id");
        json(put("/api/assignments/submissions/{id}/grade", submissionId), "{\"grade\":80}").andExpect(status().isOk());
        String memberGrade = "/api/assignments/submissions/{id}/members/{s}/grade";
        json(put(memberGrade, submissionId, second), "{\"grade\":60}").andExpect(status().isBadRequest());
        json(put(memberGrade, submissionId, loner), "{\"grade\":60,\"reason\":\"x\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errorCode").value("GROUP_MEMBER_NOT_FOUND"));
        json(put(memberGrade, submissionId, second), "{\"grade\":60,\"reason\":\"Katkı düşük\"}").andExpect(status().isOk());

        mockMvc.perform(as(get("/api/assignments/course/{id}/gradebook", courseId), TestTokens.academician(instructor)))
                .andExpect(jsonPath("$.students[?(@.studentId == '" + first + "')].grades[0].grade").value(80.0))
                .andExpect(jsonPath("$.students[?(@.studentId == '" + second + "')].grades[0].grade").value(60.0))
                .andExpect(jsonPath("$.students[?(@.studentId == '" + loner + "')].grades[0].status").value("NOT_SUBMITTED"));
        mockMvc.perform(as(post("/api/assignments/{id}/publish-grades", assignmentId), TestTokens.academician(instructor)))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/assignments/my-assignments"), TestTokens.student(second)))
                .andExpect(jsonPath("$[0].submission.grade").value(60.0));
        mockMvc.perform(as(get("/api/assignments/course/{id}/my-grades", courseId), TestTokens.student(first)))
                .andExpect(jsonPath("$.grades[0].grade").value(80.0))
                .andExpect(jsonPath("$.weightedTotal").value(40.0));
        mockMvc.perform(as(get("/api/assignments/submissions/{id}/grade-history", submissionId), TestTokens.academician(instructor)))
                .andExpect(jsonPath("$[0].studentId").value(second.toString()))
                .andExpect(jsonPath("$[0].reason").value("Katkı düşük"));

        mockMvc.perform(as(delete(memberGrade, submissionId, second), TestTokens.academician(instructor)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(get("/api/assignments/my-assignments"), TestTokens.student(second)))
                .andExpect(jsonPath("$[0].submission.grade").value(80.0));
    }

    @Test
    void aDeletedSubmitterLeavesTheGroupWorkToTheOtherMembers() throws Exception {
        String submissionId = JsonPath.read(submit(second, "Teslim").andReturn().getResponse()
                .getContentAsString(StandardCharsets.UTF_8), "$.id");

        cleanupService.deleteUserData(second);

        AssignmentSubmission kept = submissionRepository.findById(UUID.fromString(submissionId)).orElseThrow();
        assertThat(kept.getStudentId()).isEqualTo(first);
        assertThat(memberRepository.findByGroupSetIdAndStudentId(set.getId(), second)).isEmpty();
    }

    private ResultActions create(String extra) throws Exception {
        String json = "{\"title\":\"Grup projesi\",\"dueDate\":\"" + LocalDateTime.now().plusDays(3).withNano(0)
                + "\",\"courseId\":\"" + courseId + "\"," + extra.substring(1);
        MockMultipartFile part = new MockMultipartFile("assignment", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(as(multipart("/api/assignments").file(part), TestTokens.academician(instructor)));
    }

    private ResultActions submit(UUID studentId, String text) throws Exception {
        return mockMvc.perform(as(multipart("/api/assignments/{id}/submit", assignmentId).param("text", text),
                TestTokens.student(studentId)));
    }

    private ResultActions json(AbstractMockHttpServletRequestBuilder<?> request, String body) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.academician(instructor)))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
