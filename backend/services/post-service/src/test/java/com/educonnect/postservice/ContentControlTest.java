package com.educonnect.postservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.postservice.client.ClubClient;
import com.educonnect.postservice.client.CourseClient;
import com.educonnect.postservice.dto.ClubAccess;
import com.educonnect.postservice.dto.CourseAccess;
import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.CommentStatus;
import com.educonnect.postservice.model.ContentReport;
import com.educonnect.postservice.model.ModerationTarget;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.model.PublisherType;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.ContentReportRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostIntegrationTest
class ContentControlTest {

    private final UUID author = UUID.randomUUID();
    private final UUID moderator = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ContentReportRepository reportRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ClubClient clubClient;

    @MockitoBean
    private CourseClient courseClient;

    @Test
    void readersReportPublishedContentOnceAndSensitiveReportsPointToSupport() throws Exception {
        UUID postId = savePost(null, null);
        UUID reader = UUID.randomUUID();

        mockMvc.perform(as(post("/api/posts/{id}/report", postId), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_REPORT_OWN"));
        mockMvc.perform(as(post("/api/posts/{id}/report", postId), TestTokens.student(reader))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"HARASSMENT\",\"details\":\"Mesajlarla rahatsız ediyor\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sensitive").value(true))
                .andExpect(jsonPath("$.reasonLabel").value("Taciz"))
                .andExpect(jsonPath("$.supportMessage").isNotEmpty());
        mockMvc.perform(as(post("/api/posts/{id}/report", postId), TestTokens.student(reader))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ALREADY_REPORTED"));
        mockMvc.perform(as(post("/api/posts/{id}/report", postId), TestTokens.student(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.supportMessage").doesNotExist());

        assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
        mockMvc.perform(as(get("/api/posts/moderation/reports").param("size", "200"), TestTokens.student(reader)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/posts/moderation/reports").param("size", "200"), moderatorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sensitive").value(true))
                .andExpect(jsonPath("$.content[?(@.postId == '" + postId + "')].openReportsOnTarget").value(everyItem(is(2))));
    }

    @Test
    void enoughReportsHideContentUntilAModeratorRestoresIt() throws Exception {
        UUID postId = savePost(null, null);
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(as(post("/api/posts/{id}/report", postId), TestTokens.student(UUID.randomUUID()))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"BULLYING\"}"))
                    .andExpect(status().isCreated());
        }

        Post hidden = postRepository.findById(postId).orElseThrow();
        assertThat(hidden.getStatus()).isEqualTo(PostStatus.HIDDEN);
        mockMvc.perform(as(get("/api/posts/{id}", postId), TestTokens.student(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/posts/{id}", postId), TestTokens.student(author)))
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.reviewNote").isNotEmpty());
        mockMvc.perform(as(put("/api/posts/{id}", postId), TestTokens.student(author)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Yeni\",\"content\":\"Yeni\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONTENT_LOCKED"));

        mockMvc.perform(as(post("/api/posts/moderation/posts/{id}/restore", postId), moderatorToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Eleştiri, kural ihlali yok\"}"))
                .andExpect(status().isNoContent());
        assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(reportRepository.findByTargetTypeAndTargetIdAndStatus(ModerationTarget.POST, postId, ContentReport.Status.DISMISSED))
                .hasSize(3);
    }

    @Test
    void removingReportedContentUpholdsTheReportsAndRewardsTheReporters() throws Exception {
        UUID postId = savePost(null, null);
        UUID commentId = saveComment(postId, UUID.randomUUID());
        UUID reporter = UUID.randomUUID();
        mockMvc.perform(as(post("/api/posts/comments/{id}/report", commentId), TestTokens.student(reporter))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"PERSONAL_DATA\"}"))
                .andExpect(status().isCreated());
        UUID reportId = reportRepository.findByTargetTypeAndTargetIdAndStatus(ModerationTarget.COMMENT, commentId,
                ContentReport.Status.OPEN).getFirst().getId();

        mockMvc.perform(as(post("/api/posts/moderation/comments/{id}/remove", commentId), TestTokens.student(reporter))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Telefon numarası paylaşılmış\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/posts/moderation/comments/{id}/remove", commentId), moderatorToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Telefon numarası paylaşılmış\"}"))
                .andExpect(status().isNoContent());

        assertThat(commentRepository.findById(commentId).orElseThrow().getStatus()).isEqualTo(CommentStatus.REMOVED);
        assertThat(reportRepository.findById(reportId).orElseThrow().getStatus()).isEqualTo(ContentReport.Status.UPHELD);
        Integer rewards = jdbcTemplate.queryForObject("select count(*) from outbox_messages where routing_key = ? "
                + "and convert_from(body, 'UTF8') like ?", Integer.class, "gamification.report.resolved", "%" + reportId + "%");
        assertThat(rewards).isEqualTo(1);
        mockMvc.perform(as(post("/api/posts/moderation/comments/{id}/remove", commentId), moderatorToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Tekrar\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CONTENT_STATE"));
    }

    @Test
    void moderatorsDismissUnfoundedReportsWithAReason() throws Exception {
        UUID postId = savePost(null, null);
        mockMvc.perform(as(post("/api/posts/{id}/report", postId), TestTokens.student(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"WRONG_CATEGORY\"}"))
                .andExpect(status().isCreated());
        UUID reportId = reportRepository.findByTargetTypeAndTargetIdAndStatus(ModerationTarget.POST, postId,
                ContentReport.Status.OPEN).getFirst().getId();

        mockMvc.perform(as(post("/api/posts/moderation/reports/{id}/dismiss", reportId), moderatorToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Kategori doğru\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(post("/api/posts/moderation/reports/{id}/dismiss", reportId), moderatorToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Kategori doğru\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REPORT_CLOSED"));
        ContentReport dismissed = reportRepository.findById(reportId).orElseThrow();
        assertThat(dismissed.getStatus()).isEqualTo(ContentReport.Status.DISMISSED);
        assertThat(dismissed.getResolvedBy()).isEqualTo(moderator);
    }

    @Test
    void scopeOwnersHideContentInTheirOwnSpaceOnly() throws Exception {
        UUID clubId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID president = UUID.randomUUID();
        UUID instructor = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        when(clubClient.getAccess(clubId, president)).thenReturn(
                new ClubAccess(clubId, president, "PRESIDENT", true, true, false, Set.of(), "Kulüp"));
        when(clubClient.getAccess(clubId, stranger)).thenReturn(
                new ClubAccess(clubId, stranger, null, false, false, false, Set.of(), "Kulüp"));
        when(courseClient.getAccess(courseId, instructor)).thenReturn(
                new CourseAccess(courseId, "ACTIVE", true, false, "INSTRUCTOR", "BIL101", "Programlama", "1"));

        UUID ownPost = savePost(null, null);
        UUID ownComment = saveComment(ownPost, UUID.randomUUID());
        mockMvc.perform(as(post("/api/posts/comments/{id}/hide", ownComment), TestTokens.student(stranger))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Konu dışı\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_SCOPE_OWNER"));
        mockMvc.perform(as(post("/api/posts/comments/{id}/hide", ownComment), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Konu dışı\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(post("/api/posts/{id}/hide", ownPost), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Gizle\"}"))
                .andExpect(status().isForbidden());

        UUID clubPost = savePost(clubId, null);
        UUID clubComment = saveComment(clubPost, UUID.randomUUID());
        mockMvc.perform(as(post("/api/posts/comments/{id}/hide", clubComment), TestTokens.student(president))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Hakaret\"}"))
                .andExpect(status().isNoContent());

        UUID coursePost = savePost(null, courseId);
        mockMvc.perform(as(post("/api/posts/{id}/hide", coursePost), TestTokens.academician(instructor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sınav sorusu paylaşılmış\"}"))
                .andExpect(status().isNoContent());

        assertThat(commentRepository.findById(ownComment).orElseThrow().getStatus()).isEqualTo(CommentStatus.HIDDEN);
        assertThat(commentRepository.findById(clubComment).orElseThrow().getModerationNote()).isEqualTo("Hakaret");
        assertThat(postRepository.findById(coursePost).orElseThrow().getStatus()).isEqualTo(PostStatus.HIDDEN);
        mockMvc.perform(as(get("/api/posts/moderation/history").param("targetType", "POST").param("targetId", coursePost.toString()),
                        moderatorToken()))
                .andExpect(jsonPath("$[0].action").value("HIDDEN"))
                .andExpect(jsonPath("$[0].actorType").value("SCOPE_OWNER"));
    }

    private String moderatorToken() {
        return TestTokens.user(moderator, "ROLE_STAFF,PERM_MODERATOR");
    }

    private UUID savePost(UUID clubId, UUID courseId) {
        Post post = new Post();
        post.setTitle("Başlık");
        post.setContent("İçerik");
        post.setStatus(PostStatus.PUBLISHED);
        post.setAuthorId(author);
        post.setSubmittedAt(Instant.now());
        if (clubId != null) {
            post.setCategory(PostCategory.DUYURU);
            post.setPublisherType(PublisherType.CLUB);
            post.setClubId(clubId);
        } else if (courseId != null) {
            post.setCategory(PostCategory.DUYURU);
            post.setPublisherType(PublisherType.COURSE);
            post.setCourseId(courseId);
        } else {
            post.setCategory(PostCategory.SORU);
        }
        return postRepository.save(post).getId();
    }

    private UUID saveComment(UUID postId, UUID commentAuthor) {
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(commentAuthor);
        comment.setContent("Yorum");
        comment.setStatus(CommentStatus.PUBLISHED);
        return commentRepository.save(comment).getId();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
