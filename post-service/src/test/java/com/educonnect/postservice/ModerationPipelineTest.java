package com.educonnect.postservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.postservice.event.PostModerationEvent;
import com.educonnect.postservice.messaging.ModerationReviewListener;
import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.CommentStatus;
import com.educonnect.postservice.model.ModerationAction;
import com.educonnect.postservice.model.ModerationActor;
import com.educonnect.postservice.model.ModerationRecord;
import com.educonnect.postservice.model.ModerationTarget;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.ModerationRecordRepository;
import com.educonnect.postservice.repository.PostRepository;
import com.educonnect.postservice.service.PostModerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostIntegrationTest
class ModerationPipelineTest {

    private final UUID author = UUID.randomUUID();
    private final UUID reader = UUID.randomUUID();
    private final UUID moderator = UUID.randomUUID();

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

    @Autowired
    private PostModerationService moderationService;

    @Autowired
    private ModerationReviewListener reviewListener;

    @Test
    void wordListHitsAreRejectedWithAReasonWhileModelSuspicionGoesToAModerator() throws Exception {
        UUID wordList = pendingPost();
        UUID suspected = pendingPost();
        UUID undecided = pendingPost();

        decide("/api/posts/internal/{id}/moderation", wordList, "{\"decision\":\"ZORBA\",\"source\":\"WORDLIST\"}");
        decide("/api/posts/internal/{id}/moderation", suspected, "{\"decision\":\"ZORBA\",\"source\":\"LLM\"}");
        decide("/api/posts/internal/{id}/moderation", undecided, "{\"decision\":\"INCELEME\",\"source\":\"LLM\"}");

        Post rejected = postRepository.findById(wordList).orElseThrow();
        assertThat(rejected.getStatus()).isEqualTo(PostStatus.REJECTED);
        assertThat(rejected.getReviewNote()).isNotBlank();
        assertThat(postRepository.findById(suspected).orElseThrow().getStatus()).isEqualTo(PostStatus.IN_REVIEW);
        assertThat(postRepository.findById(undecided).orElseThrow().getModerationFlag()).isNotBlank();
        assertThat(records(ModerationTarget.POST, wordList)).extracting(ModerationRecord::getAction, ModerationRecord::getActorType)
                .containsExactly(tuple(ModerationAction.REJECTED, ModerationActor.AUTOMATIC));

        mockMvc.perform(as(get("/api/posts/{id}", rejected.getId()), TestTokens.student(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNote").isNotEmpty());
        mockMvc.perform(as(get("/api/posts/{id}", suspected), TestTokens.student(reader)))
                .andExpect(status().isNotFound());
    }

    @Test
    void moderatorsWorkTheQueueWithReasonedDecisions() throws Exception {
        UUID approved = pendingPost();
        UUID rejected = pendingPost();
        decide("/api/posts/internal/{id}/moderation", approved, "{\"decision\":\"INCELEME\"}");
        decide("/api/posts/internal/{id}/moderation", rejected, "{\"decision\":\"ZORBA\"}");

        mockMvc.perform(as(get("/api/posts/moderation/queue"), TestTokens.student(reader)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_MODERATOR"));
        mockMvc.perform(as(get("/api/posts/moderation/queue").param("size", "200"), moderatorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + approved + "')].flag").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.id == '" + rejected + "')]").exists());

        mockMvc.perform(as(post("/api/posts/moderation/posts/{id}/decision", approved), TestTokens.student(reader))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/posts/moderation/posts/{id}/decision", approved), moderatorToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"APPROVE\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(post("/api/posts/moderation/posts/{id}/decision", rejected), moderatorToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\",\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("REASON_REQUIRED"));
        mockMvc.perform(as(post("/api/posts/moderation/posts/{id}/decision", rejected), TestTokens.admin(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\",\"reason\":\"Kişisel veri içeriyor\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(post("/api/posts/moderation/posts/{id}/decision", approved), moderatorToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"REJECT\",\"reason\":\"Geç\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NOT_IN_REVIEW"));

        assertThat(postRepository.findById(approved).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
        Post rejectedPost = postRepository.findById(rejected).orElseThrow();
        assertThat(rejectedPost.getStatus()).isEqualTo(PostStatus.REJECTED);
        assertThat(rejectedPost.getReviewNote()).isEqualTo("Kişisel veri içeriyor");

        mockMvc.perform(as(get("/api/posts/moderation/history").param("targetType", "POST").param("targetId", approved.toString()),
                        moderatorToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("PUBLISHED"))
                .andExpect(jsonPath("$[0].actorType").value("MODERATOR"))
                .andExpect(jsonPath("$[0].actorId").value(moderator.toString()))
                .andExpect(jsonPath("$[1].action").value("SENT_TO_REVIEW"));
    }

    @Test
    void commentsWaitForModerationAndOnlyTheirAuthorSeesThemMeanwhile() throws Exception {
        UUID postId = publishedPost();
        String response = mockMvc.perform(as(post("/api/posts/{id}/comments", postId), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Kadına şiddete hayır, kargo ile geldi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        UUID commentId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mockMvc.perform(as(get("/api/posts/{id}/comments", postId), TestTokens.student(reader)))
                .andExpect(jsonPath("$.content[?(@.id == '" + commentId + "')]").doesNotExist());
        mockMvc.perform(as(get("/api/posts/{id}/comments", postId), TestTokens.student(author)))
                .andExpect(jsonPath("$.content[?(@.id == '" + commentId + "')].status").value("PENDING"));
        mockMvc.perform(as(post("/api/posts/comments/{id}/replies", commentId), TestTokens.student(reader))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Katılıyorum\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(get("/api/posts/{id}", postId), TestTokens.student(reader)))
                .andExpect(jsonPath("$.commentCount").value(0));

        mockMvc.perform(as(put("/api/posts/internal/comments/{id}/moderation", commentId), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"TEMIZ\"}"))
                .andExpect(status().isForbidden());
        decide("/api/posts/internal/comments/{id}/moderation", commentId, "{\"decision\":\"TEMIZ\",\"source\":\"LLM\"}");

        mockMvc.perform(as(get("/api/posts/{id}/comments", postId), TestTokens.student(reader)))
                .andExpect(jsonPath("$.content[?(@.id == '" + commentId + "')].status").value("PUBLISHED"));
        mockMvc.perform(as(get("/api/posts/{id}", postId), TestTokens.student(reader)))
                .andExpect(jsonPath("$.commentCount").value(1));
    }

    @Test
    void rejectedCommentsTellTheirAuthorWhyAndSuspiciousOnesReachTheModerator() throws Exception {
        UUID postId = publishedPost();
        UUID rejected = pendingComment(postId);
        UUID suspected = pendingComment(postId);

        decide("/api/posts/internal/comments/{id}/moderation", rejected, "{\"decision\":\"ZORBA\",\"source\":\"WORDLIST\"}");
        decide("/api/posts/internal/comments/{id}/moderation", suspected, "{\"decision\":\"ZORBA\",\"source\":\"LLM\"}");

        mockMvc.perform(as(get("/api/posts/{id}/comments", postId), TestTokens.student(author)))
                .andExpect(jsonPath("$.content[?(@.id == '" + rejected + "')].status").value("REJECTED"))
                .andExpect(jsonPath("$.content[?(@.id == '" + rejected + "')].moderationNote").isNotEmpty());
        mockMvc.perform(as(get("/api/posts/moderation/queue").param("type", "COMMENT").param("size", "200"), moderatorToken()))
                .andExpect(jsonPath("$.content[?(@.id == '" + suspected + "')].postId").value(postId.toString()));
        mockMvc.perform(as(post("/api/posts/moderation/comments/{id}/decision", suspected), moderatorToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"APPROVE\",\"reason\":\"Eleştiri, hakaret değil\"}"))
                .andExpect(status().isNoContent());
        assertThat(commentRepository.findById(suspected).orElseThrow().getStatus()).isEqualTo(CommentStatus.PUBLISHED);
        assertThat(records(ModerationTarget.COMMENT, suspected)).extracting(ModerationRecord::getReason)
                .contains("Eleştiri, hakaret değil");
    }

    @Test
    void contentStuckInAutomaticModerationOrReturnedUndecidedReachesTheModeratorQueue() {
        UUID stale = pendingPost();
        UUID staleComment = pendingComment(publishedPost());
        UUID undecided = pendingPost();

        moderationService.escalateStale(Instant.now().plusSeconds(60));
        reviewListener.handle(new PostModerationEvent(undecided, "Başlık", "İçerik", UUID.randomUUID()));

        assertThat(postRepository.findById(stale).orElseThrow().getStatus()).isEqualTo(PostStatus.IN_REVIEW);
        assertThat(commentRepository.findById(staleComment).orElseThrow().getStatus()).isEqualTo(CommentStatus.IN_REVIEW);
        assertThat(postRepository.findById(undecided).orElseThrow().getStatus()).isEqualTo(PostStatus.IN_REVIEW);
    }

    private void decide(String path, UUID id, String body) throws Exception {
        mockMvc.perform(as(put(path, id), TestTokens.service("llm-service")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
    }

    private List<ModerationRecord> records(ModerationTarget type, UUID id) {
        return recordRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(type, id);
    }

    private String moderatorToken() {
        return TestTokens.user(moderator, "ROLE_STAFF,PERM_MODERATOR");
    }

    private UUID pendingPost() {
        return savePost(PostStatus.PENDING);
    }

    private UUID publishedPost() {
        return savePost(PostStatus.PUBLISHED);
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

    private UUID pendingComment(UUID postId) {
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(author);
        comment.setContent("Yorum");
        comment.setStatus(CommentStatus.PENDING);
        comment.setSubmittedAt(Instant.now());
        return commentRepository.save(comment).getId();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
