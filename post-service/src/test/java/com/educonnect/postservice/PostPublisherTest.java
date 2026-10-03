package com.educonnect.postservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.postservice.client.ClubClient;
import com.educonnect.postservice.client.CourseClient;
import com.educonnect.postservice.dto.ClubAccess;
import com.educonnect.postservice.dto.CourseAccess;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.model.PublisherType;
import com.educonnect.postservice.repository.PostRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostIntegrationTest
class PostPublisherTest {

    private final UUID clubId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final UUID president = UUID.randomUUID();
    private final UUID officer = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private final UUID instructor = UUID.randomUUID();
    private final UUID assistant = UUID.randomUUID();
    private final UUID enrolled = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PostRepository postRepository;

    @MockitoBean
    private ClubClient clubClient;

    @MockitoBean
    private CourseClient courseClient;

    @BeforeEach
    void stubScopes() {
        when(clubClient.getAccess(clubId, president)).thenReturn(club(president, "PRESIDENT", true, false, Set.of("PREPARE_ANNOUNCEMENT", "APPROVE_AS_PRESIDENT")));
        when(clubClient.getAccess(clubId, officer)).thenReturn(club(officer, "COMMUNICATIONS_OFFICER", false, false, Set.of("PREPARE_ANNOUNCEMENT")));
        when(clubClient.getAccess(clubId, advisor)).thenReturn(club(advisor, null, false, true, Set.of("ADVISE")));
        when(clubClient.getAccess(clubId, outsider)).thenReturn(club(outsider, "MEMBER", false, false, Set.of()));
        when(courseClient.getAccess(courseId, instructor)).thenReturn(course("COORDINATOR", true, false));
        when(courseClient.getAccess(courseId, assistant)).thenReturn(course("ASSISTANT", false, false));
        when(courseClient.getAccess(courseId, enrolled)).thenReturn(course(null, false, true));
        when(courseClient.getAccess(courseId, outsider)).thenReturn(course(null, false, false));
        when(courseClient.getStudentCourseIds(enrolled)).thenReturn(List.of(courseId));
        when(courseClient.getStudentCourseIds(outsider)).thenReturn(List.of());
    }

    @Test
    void studentsWriteForumPostsAndQuestionIsTheDefaultCategory() throws Exception {
        mockMvc.perform(as(post("/api/posts"), TestTokens.student(outsider)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Soru\",\"content\":\"Nasıl?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("SORU"))
                .andExpect(jsonPath("$.publisherType").value("STUDENT"))
                .andExpect(jsonPath("$.official").value(false));
        mockMvc.perform(as(post("/api/posts"), TestTokens.student(outsider)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Duyuru\",\"content\":\"Herkese\",\"category\":\"DUYURU\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PUBLISHER_REQUIRED"));
        mockMvc.perform(as(post("/api/posts"), TestTokens.student(outsider)).contentType(MediaType.APPLICATION_JSON)
                        .content(announcement("\"publisherType\":\"CLUB\",\"clubId\":\"" + clubId + "\"", "GENEL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CATEGORY"));
    }

    @Test
    void clubAnnouncementPreparedByAnOfficerWaitsForThePresident() throws Exception {
        UUID postId = create(TestTokens.student(officer), clubAnnouncement(), "AWAITING_APPROVAL");
        assertThat(postRepository.findById(postId).orElseThrow().getPublisherName()).isEqualTo("Satranç Kulübü");

        mockMvc.perform(as(get("/api/posts/{id}", postId), TestTokens.student(outsider)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/posts/club/{clubId}/awaiting-approval", clubId), TestTokens.student(officer)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_CLUB_APPROVER"));
        mockMvc.perform(as(get("/api/posts/club/{clubId}/awaiting-approval", clubId), TestTokens.student(president)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(postId.toString()));
        mockMvc.perform(as(post("/api/posts/{id}/approve", postId), TestTokens.student(outsider)))
                .andExpect(status().isForbidden());

        mockMvc.perform(as(post("/api/posts/{id}/approve", postId), TestTokens.student(president)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        Post approved = postRepository.findById(postId).orElseThrow();
        assertThat(approved.getApprovedBy()).isEqualTo(president);
        mockMvc.perform(as(post("/api/posts/{id}/approve", postId), TestTokens.student(president)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOT_AWAITING_APPROVAL"));

        publish(postId);
        mockMvc.perform(as(get("/api/posts").param("official", "true").param("size", "100"), TestTokens.student(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + postId + "')].publisherName").value("Satranç Kulübü"))
                .andExpect(jsonPath("$.content[?(@.id == '" + postId + "')].official").value(true));
    }

    @Test
    void presidentRejectsAClubAnnouncementWithAReason() throws Exception {
        UUID postId = create(TestTokens.student(officer), clubAnnouncement(), "AWAITING_APPROVAL");

        mockMvc.perform(as(post("/api/posts/{id}/reject", postId), TestTokens.student(president))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(post("/api/posts/{id}/reject", postId), TestTokens.student(president))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Tarih yanlış\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").value("Tarih yanlış"));

        mockMvc.perform(as(put("/api/posts/{id}", postId), TestTokens.student(officer)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Düzeltildi\",\"content\":\"Yeni tarih\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
                .andExpect(jsonPath("$.reviewNote").doesNotExist());
    }

    @Test
    void presidentAndAdvisorPublishClubAnnouncementsDirectlyButOtherMembersCannot() throws Exception {
        create(TestTokens.student(president), clubAnnouncement(), "PENDING");
        create(TestTokens.academician(advisor), clubAnnouncement(), "PENDING");

        mockMvc.perform(as(post("/api/posts"), TestTokens.student(outsider)).contentType(MediaType.APPLICATION_JSON)
                        .content(clubAnnouncement()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_CLUB_PUBLISHER"));
        mockMvc.perform(as(post("/api/posts"), TestTokens.student(president)).contentType(MediaType.APPLICATION_JSON)
                        .content(announcement("\"publisherType\":\"CLUB\"", "DUYURU")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_REQUIRED"));
    }

    @Test
    void courseAnnouncementsComeFromTeachingStaffAndReachOnlyCourseMembers() throws Exception {
        mockMvc.perform(as(post("/api/posts"), TestTokens.academician(assistant)).contentType(MediaType.APPLICATION_JSON)
                        .content(courseAnnouncement()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_COURSE_PUBLISHER"));
        UUID postId = create(TestTokens.academician(instructor), courseAnnouncement(), "PENDING");
        assertThat(postRepository.findById(postId).orElseThrow().getPublisherName()).isEqualTo("BIL101 Programlama (Şube 2)");
        publish(postId);

        mockMvc.perform(as(get("/api/posts").param("size", "100"), TestTokens.student(outsider)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + postId + "')]").doesNotExist());
        mockMvc.perform(as(get("/api/posts/{id}", postId), TestTokens.student(outsider)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/posts").param("publisherType", "COURSE").param("size", "100"), TestTokens.student(enrolled)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + postId + "')]").exists());
        mockMvc.perform(as(get("/api/posts/{id}", postId), TestTokens.student(enrolled)))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/posts").param("courseId", courseId.toString()), TestTokens.admin(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + postId + "')]").exists());
    }

    @Test
    void campusAnnouncementsNeedTheCampusPublisherPermissionAndAUnitName() throws Exception {
        String campus = announcement("\"publisherType\":\"CAMPUS\",\"publisherName\":\"Sağlık Kültür ve Spor\"", "DUYURU");
        mockMvc.perform(as(post("/api/posts"), TestTokens.user(UUID.randomUUID(), "ROLE_STAFF,PERM_MODERATOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(campus))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_CAMPUS_PUBLISHER"));
        mockMvc.perform(as(post("/api/posts"), TestTokens.user(UUID.randomUUID(), "ROLE_STAFF,PERM_CAMPUS_PUBLISHER"))
                        .contentType(MediaType.APPLICATION_JSON).content(announcement("\"publisherType\":\"CAMPUS\"", "DUYURU")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PUBLISHER_NAME_REQUIRED"));

        UUID postId = create(TestTokens.user(UUID.randomUUID(), "ROLE_STAFF,PERM_CAMPUS_PUBLISHER"), campus, "PENDING");
        Post saved = postRepository.findById(postId).orElseThrow();
        assertThat(saved.getPublisherType()).isEqualTo(PublisherType.CAMPUS);
        assertThat(saved.getPublisherName()).isEqualTo("Sağlık Kültür ve Spor");
        create(TestTokens.admin(UUID.randomUUID()), campus, "PENDING");
    }

    @Test
    void commentsCanBeTurnedOffOnAnnouncementsAndCategoriesCannotCrossTheForumLine() throws Exception {
        UUID postId = create(TestTokens.student(president),
                announcement("\"publisherType\":\"CLUB\",\"clubId\":\"" + clubId + "\",\"commentsDisabled\":true", "DUYURU"),
                "PENDING");
        publish(postId);

        mockMvc.perform(as(post("/api/posts/{id}/comments", postId), TestTokens.student(outsider))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Harika\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("COMMENTS_DISABLED"));
        mockMvc.perform(as(put("/api/posts/{id}", postId), TestTokens.student(president)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Başlık\",\"content\":\"İçerik\",\"category\":\"GENEL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_CHANGE_NOT_ALLOWED"));

        UUID forumPost = create(TestTokens.student(outsider), "{\"title\":\"Not\",\"content\":\"Özet\",\"category\":\"DERS_NOTU\",\"sharingDeclaration\":true}", "PENDING");
        mockMvc.perform(as(put("/api/posts/{id}", forumPost), TestTokens.student(outsider)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Not\",\"content\":\"Özet\",\"category\":\"DUYURU\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CATEGORY_CHANGE_NOT_ALLOWED"));
    }

    private UUID create(String token, String body, String expectedStatus) throws Exception {
        String response = mockMvc.perform(as(post("/api/posts"), token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return UUID.fromString(node.get("id").asText());
    }

    private void publish(UUID postId) throws Exception {
        mockMvc.perform(as(put("/api/posts/internal/{id}/moderation", postId), TestTokens.service("llm-service"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"TEMIZ\"}"))
                .andExpect(status().isAccepted());
        assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    private String clubAnnouncement() {
        return announcement("\"publisherType\":\"CLUB\",\"clubId\":\"" + clubId + "\"", "DUYURU");
    }

    private String courseAnnouncement() {
        return announcement("\"publisherType\":\"COURSE\",\"courseId\":\"" + courseId + "\"", "DUYURU");
    }

    private static String announcement(String publisher, String category) {
        return "{\"title\":\"Toplantı\",\"content\":\"Cuma 15.00\",\"category\":\"" + category + "\"," + publisher + "}";
    }

    private ClubAccess club(UUID userId, String position, boolean actingPresident, boolean advisor, Set<String> permissions) {
        return new ClubAccess(clubId, userId, position, position != null, actingPresident, advisor, permissions, "Satranç Kulübü");
    }

    private CourseAccess course(String staffRole, boolean teaches, boolean isEnrolled) {
        return new CourseAccess(courseId, "ACTIVE", teaches, isEnrolled, staffRole, "BIL101", "Programlama", "2");
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
