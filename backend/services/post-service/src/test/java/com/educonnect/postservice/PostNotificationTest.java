package com.educonnect.postservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.postservice.client.ClubClient;
import com.educonnect.postservice.client.CourseClient;
import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.CommentStatus;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostIntegrationTest
class PostNotificationTest {

    private final UUID author = UUID.randomUUID();
    private final UUID moderator = UUID.randomUUID();
    private final UUID secondModerator = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ClubClient clubClient;

    @MockitoBean
    private CourseClient courseClient;

    @Test
    void hiddenContentTellsItsAuthorAndRemovalTellsTheReporters() throws Exception {
        UUID postId = savePost(PostStatus.PUBLISHED);
        List<UUID> reporters = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        for (UUID reporter : reporters) {
            json(post("/api/posts/{id}/report", postId), TestTokens.student(reporter), "{\"reason\":\"BULLYING\"}")
                    .andExpect(status().isCreated());
        }
        assertThat(notifications(postId, "MODERATION_HIDDEN")).singleElement().asString()
                .contains(author.toString(), "\"category\":\"MODERATION\"", "Gönderiniz gizlendi", "Birden fazla şikâyet",
                        "itiraz", "\"link\":\"/posts/" + postId + "\"");

        json(post("/api/posts/moderation/posts/{id}/remove", postId), moderatorToken(moderator), "{\"reason\":\"Zorbalık\"}")
                .andExpect(status().is2xxSuccessful());
        assertThat(notifications(postId, "MODERATION_REMOVED")).singleElement().asString()
                .contains(author.toString(), "Gönderiniz kaldırıldı", "Zorbalık");
        assertThat(notifications(postId, "REPORT_RESOLVED")).singleElement().asString()
                .contains(reporters.get(0).toString(), reporters.get(1).toString(), reporters.get(2).toString(), "kaldırıldı")
                .doesNotContain(author.toString());

        json(post("/api/posts/{id}/appeal", postId), TestTokens.student(author), "{\"statement\":\"Eleştiriydi\"}")
                .andExpect(status().isCreated());
        String appealId = jdbcTemplate.queryForObject("select id::text from moderation_appeals where target_id = ?", String.class, postId);
        json(post("/api/posts/moderation/appeals/{id}/decision", appealId), moderatorToken(secondModerator),
                "{\"outcome\":\"REJECT\",\"reason\":\"Karar yerinde\"}")
                .andExpect(status().isOk());
        assertThat(notifications(postId, "MODERATION_APPEAL_REJECTED")).singleElement().asString()
                .contains(author.toString(), "İtirazınız reddedildi", "Karar yerinde");
    }

    @Test
    void reviewedCommentsReachThePostAuthorAndRepliesTheParentAuthor() throws Exception {
        UUID postId = savePost(PostStatus.PUBLISHED);
        UUID commenter = UUID.randomUUID();
        UUID replier = UUID.randomUUID();
        UUID topComment = saveComment(postId, commenter, null, CommentStatus.IN_REVIEW);
        UUID reply = saveComment(postId, replier, topComment, CommentStatus.IN_REVIEW);
        UUID ownComment = saveComment(postId, author, null, CommentStatus.IN_REVIEW);

        for (UUID comment : List.of(topComment, reply, ownComment)) {
            json(post("/api/posts/moderation/comments/{id}/decision", comment), moderatorToken(moderator), "{\"action\":\"APPROVE\"}")
                    .andExpect(status().is2xxSuccessful());
        }

        assertThat(notifications(postId, "POST_COMMENT")).hasSize(2)
                .allSatisfy(body -> assertThat(body).contains(author.toString(), "\"category\":\"COMMUNITY\""));
        assertThat(notifications(postId, "COMMENT_REPLY")).singleElement().asString()
                .contains(commenter.toString(), "Yorumunuza yanıt geldi").doesNotContain(author.toString());
        assertThat(notifications(postId, "MODERATION_PUBLISHED")).hasSize(3);

        json(put("/api/posts/{id}/accepted-answer", postId), TestTokens.student(author), "{\"commentId\":\"" + topComment + "\"}")
                .andExpect(status().is2xxSuccessful());
        assertThat(notifications(postId, "ANSWER_ACCEPTED")).singleElement().asString()
                .contains(commenter.toString(), "Cevabınız kabul edildi");
    }

    @Test
    void automaticPublicationIsSilentButRejectionIsExplained() throws Exception {
        UUID published = savePost(PostStatus.IN_REVIEW);
        UUID rejected = savePost(PostStatus.IN_REVIEW);
        json(post("/api/posts/moderation/posts/{id}/decision", published), moderatorToken(moderator), "{\"action\":\"APPROVE\"}")
                .andExpect(status().is2xxSuccessful());
        json(post("/api/posts/moderation/posts/{id}/decision", rejected), moderatorToken(moderator),
                "{\"action\":\"REJECT\",\"reason\":\"Kişisel veri içeriyor\"}")
                .andExpect(status().is2xxSuccessful());

        assertThat(notifications(published, "MODERATION_PUBLISHED")).singleElement().asString().contains("Gönderiniz onaylandı");
        assertThat(notifications(rejected, "MODERATION_REJECTED")).singleElement().asString()
                .contains(author.toString(), "Gönderiniz yayımlanmadı", "Kişisel veri içeriyor");
    }

    private List<String> notifications(UUID postId, String type) {
        return jdbcTemplate.queryForList("select convert_from(body, 'UTF8') from outbox_messages "
                        + "where routing_key = 'notification.request' and convert_from(body, 'UTF8') like ? "
                        + "and convert_from(body, 'UTF8') like ?",
                String.class, "%/posts/" + postId + "%", "%\"type\":\"" + type + "\"%");
    }

    private UUID savePost(PostStatus status) {
        Post post = new Post();
        post.setTitle("Bildirimli gönderi");
        post.setContent("İçerik");
        post.setStatus(status);
        post.setAuthorId(author);
        post.setSubmittedAt(Instant.now());
        post.setCategory(PostCategory.SORU);
        return postRepository.save(post).getId();
    }

    private UUID saveComment(UUID postId, UUID commentAuthor, UUID parent, CommentStatus status) {
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(commentAuthor);
        comment.setParentCommentId(parent);
        comment.setContent("Yorum");
        comment.setStatus(status);
        return commentRepository.save(comment).getId();
    }

    private static String moderatorToken(UUID id) {
        return TestTokens.user(id, "ROLE_STAFF,PERM_MODERATOR");
    }

    private ResultActions json(AbstractMockHttpServletRequestBuilder<?> request, String token, String body) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
