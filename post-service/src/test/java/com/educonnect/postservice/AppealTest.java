package com.educonnect.postservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.CommentStatus;
import com.educonnect.postservice.model.ModerationAction;
import com.educonnect.postservice.model.ModerationRecord;
import com.educonnect.postservice.model.ModerationTarget;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.ModerationRecordRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostIntegrationTest
class AppealTest {

    private final UUID author = UUID.randomUUID();
    private final UUID firstModerator = UUID.randomUUID();
    private final UUID secondModerator = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private ModerationRecordRepository recordRepository;

    @Test
    void anotherModeratorReinstatesRemovedContentOnAnAcceptedAppeal() throws Exception {
        UUID postId = savePost(PostStatus.PUBLISHED);
        mockMvc.perform(as(post("/api/posts/moderation/posts/{id}/remove", postId), moderator(firstModerator))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Reklam\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(as(post("/api/posts/{id}/appeal", postId), TestTokens.student(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statement\":\"Benim değil\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_CONTENT_AUTHOR"));
        UUID appealId = appeal("/api/posts/{id}/appeal", postId);
        mockMvc.perform(as(post("/api/posts/{id}/appeal", postId), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statement\":\"Tekrar\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("APPEAL_EXISTS"));

        mockMvc.perform(as(get("/api/posts/moderation/appeals").param("size", "200"), moderator(secondModerator)))
                .andExpect(jsonPath("$.content[?(@.id == '" + appealId + "')].appealedAction").value("REMOVED"));
        mockMvc.perform(as(post("/api/posts/moderation/appeals/{id}/decision", appealId), moderator(firstModerator))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"ACCEPT\",\"reason\":\"Haklı\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("SAME_DECIDER"));
        mockMvc.perform(as(post("/api/posts/moderation/appeals/{id}/decision", appealId), moderator(secondModerator))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"ACCEPT\",\"reason\":\"Ders duyurusu, reklam değil\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        assertThat(postRepository.findById(postId).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
        assertThat(recordRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(ModerationTarget.POST, postId))
                .extracting(ModerationRecord::getAction).first().isEqualTo(ModerationAction.APPEAL_ACCEPTED);
        mockMvc.perform(as(get("/api/posts/appeals/me"), TestTokens.student(author)))
                .andExpect(jsonPath("$[?(@.id == '" + appealId + "')].status").value("ACCEPTED"))
                .andExpect(jsonPath("$[?(@.id == '" + appealId + "')].decisionNote").value("Ders duyurusu, reklam değil"));
    }

    @Test
    void aRejectedAppealIsFinal() throws Exception {
        UUID postId = savePost(PostStatus.PUBLISHED);
        UUID commentId = saveComment(postId, author, CommentStatus.PENDING);
        mockMvc.perform(as(put("/api/posts/internal/comments/{id}/moderation", commentId), TestTokens.service("llm-service"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"ZORBA\",\"source\":\"WORDLIST\"}"))
                .andExpect(status().isAccepted());

        UUID appealId = appeal("/api/posts/comments/{id}/appeal", commentId);
        mockMvc.perform(as(post("/api/posts/moderation/appeals/{id}/decision", appealId), moderator(firstModerator))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"REJECT\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(post("/api/posts/moderation/appeals/{id}/decision", appealId), moderator(firstModerator))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"REJECT\",\"reason\":\"Hakaret içeriyor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(as(post("/api/posts/moderation/appeals/{id}/decision", appealId), moderator(secondModerator))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"ACCEPT\",\"reason\":\"Fikrimi değiştirdim\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("APPEAL_CLOSED"));
        assertThat(commentRepository.findById(commentId).orElseThrow().getStatus()).isEqualTo(CommentStatus.REJECTED);
    }

    @Test
    void onlyModerationOutcomesCanBeAppealed() throws Exception {
        UUID published = savePost(PostStatus.PUBLISHED);
        UUID rejectedByPresident = savePost(PostStatus.REJECTED);

        mockMvc.perform(as(post("/api/posts/{id}/appeal", published), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statement\":\"Neden?\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOT_APPEALABLE"));
        mockMvc.perform(as(post("/api/posts/{id}/appeal", rejectedByPresident), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statement\":\"Neden?\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOT_APPEALABLE"));
        mockMvc.perform(as(get("/api/posts/moderation/appeals"), TestTokens.student(author)))
                .andExpect(status().isForbidden());
    }

    @Test
    void contentHiddenByItsScopeOwnerCanBeAppealedToAModerator() throws Exception {
        UUID postId = savePost(PostStatus.PUBLISHED);
        UUID commentId = saveComment(postId, UUID.randomUUID(), CommentStatus.PUBLISHED);
        mockMvc.perform(as(post("/api/posts/comments/{id}/hide", commentId), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Beğenmedim\"}"))
                .andExpect(status().isNoContent());
        Comment hidden = commentRepository.findById(commentId).orElseThrow();
        UUID commenter = hidden.getAuthorId();

        String body = mockMvc.perform(as(post("/api/posts/comments/{id}/appeal", commentId), TestTokens.student(commenter))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statement\":\"Kibar bir eleştiriydi\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID appealId = UUID.fromString(objectMapper.readTree(body).get("id").asText());
        mockMvc.perform(as(post("/api/posts/moderation/appeals/{id}/decision", appealId), moderator(firstModerator))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"ACCEPT\",\"reason\":\"Kural ihlali yok\"}"))
                .andExpect(status().isOk());
        assertThat(commentRepository.findById(commentId).orElseThrow().getStatus()).isEqualTo(CommentStatus.PUBLISHED);
    }

    private UUID appeal(String path, UUID id) throws Exception {
        String body = mockMvc.perform(as(post(path, id), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"statement\":\"Kurallara uygundu\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private String moderator(UUID id) {
        return TestTokens.user(id, "ROLE_STAFF,PERM_MODERATOR");
    }

    private UUID savePost(PostStatus status) {
        Post post = new Post();
        post.setTitle("Başlık");
        post.setContent("İçerik");
        post.setCategory(PostCategory.SORU);
        post.setStatus(status);
        post.setAuthorId(author);
        post.setSubmittedAt(Instant.now());
        return postRepository.save(post).getId();
    }

    private UUID saveComment(UUID postId, UUID commentAuthor, CommentStatus status) {
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(commentAuthor);
        comment.setContent("Yorum");
        comment.setStatus(status);
        comment.setSubmittedAt(Instant.now());
        return commentRepository.save(comment).getId();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
