package com.educonnect.postservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.CommentStatus;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.PostLikeRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostIntegrationTest
class PostAuthorizationTest {

    private static final String POST_BODY = "{\"title\":\"Vize notları\",\"content\":\"Özet\",\"category\":\"SORU\"}";
    private static final String UPDATED_BODY = "{\"title\":\"Değişti\",\"content\":\"Yeni içerik\",\"category\":\"SORU\"}";
    private static final String COMMENT_BODY = "{\"content\":\"Teşekkürler\"}";

    private final UUID author = UUID.randomUUID();
    private final UUID otherStudent = UUID.randomUUID();
    private final UUID academician = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    private UUID publishedId;
    private UUID pendingId;
    private UUID rejectedId;
    private UUID commentId;

    @BeforeEach
    void seed() {
        publishedId = savePost(PostStatus.PUBLISHED);
        pendingId = savePost(PostStatus.PENDING);
        rejectedId = savePost(PostStatus.REJECTED);
        commentId = saveComment(publishedId, author, null);
    }

    @Test
    void onlyStudentsWriteForumPostsWhileEveryCommunityRoleReads() throws Exception {
        mockMvc.perform(as(post("/api/posts"), TestTokens.academician(academician)).contentType(MediaType.APPLICATION_JSON).content(POST_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORUM_POSTING_NOT_ALLOWED"));
        mockMvc.perform(as(post("/api/posts"), TestTokens.admin(admin)).contentType(MediaType.APPLICATION_JSON).content(POST_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORUM_POSTING_NOT_ALLOWED"));
        mockMvc.perform(as(get("/api/posts"), TestTokens.user(UUID.randomUUID(), "ROLE_PENDING_STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("POST_ACCESS_DENIED"));
        mockMvc.perform(as(get("/api/posts"), TestTokens.academician(academician)))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/posts/{id}", publishedId), TestTokens.admin(admin)))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/posts/{id}", publishedId), TestTokens.user(UUID.randomUUID(), "ROLE_STAFF,PERM_MODERATOR")))
                .andExpect(status().isOk());
        assertThat(postRepository.findByAuthorId(academician, Pageable.unpaged())).isEmpty();

        mockMvc.perform(as(post("/api/posts"), TestTokens.student(otherStudent)).contentType(MediaType.APPLICATION_JSON).content(POST_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.authorId").value(otherStudent.toString()));
        UUID official = UUID.randomUUID();
        mockMvc.perform(as(post("/api/posts"), TestTokens.user(official, "ROLE_CLUB_OFFICIAL")).contentType(MediaType.APPLICATION_JSON).content(POST_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(official.toString()));
    }

    @Test
    void onlyTheAuthorUpdatesAPost() throws Exception {
        mockMvc.perform(as(put("/api/posts/{id}", publishedId), TestTokens.student(otherStudent))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("POST_ACCESS_DENIED"));
        mockMvc.perform(as(put("/api/posts/{id}", publishedId), TestTokens.user(otherStudent, "ROLE_CLUB_OFFICIAL"))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isForbidden());
        Post untouched = postRepository.findById(publishedId).orElseThrow();
        assertThat(untouched.getTitle()).isEqualTo("Başlık");
        assertThat(untouched.getStatus()).isEqualTo(PostStatus.PUBLISHED);

        mockMvc.perform(as(put("/api/posts/{id}", publishedId), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATED_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(postRepository.findById(publishedId).orElseThrow().getTitle()).isEqualTo("Değişti");
    }

    @Test
    void onlyTheAuthorDeletesAPost() throws Exception {
        mockMvc.perform(as(delete("/api/posts/{id}", publishedId), TestTokens.student(otherStudent)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("POST_ACCESS_DENIED"));
        mockMvc.perform(as(delete("/api/posts/{id}", publishedId), TestTokens.admin(admin)))
                .andExpect(status().isForbidden());
        assertThat(postRepository.existsById(publishedId)).isTrue();

        mockMvc.perform(as(delete("/api/posts/{id}", publishedId), TestTokens.student(author)))
                .andExpect(status().isNoContent());
        assertThat(postRepository.existsById(publishedId)).isFalse();
    }

    @Test
    void onlyTheAuthorDeletesACommentOrReply() throws Exception {
        UUID replyId = saveComment(publishedId, author, commentId);

        mockMvc.perform(as(delete("/api/posts/{postId}/comments/{commentId}", publishedId, commentId), TestTokens.student(otherStudent)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("POST_ACCESS_DENIED"));
        mockMvc.perform(as(delete("/api/posts/{postId}/comments/{commentId}", publishedId, replyId), TestTokens.student(otherStudent)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(delete("/api/posts/{postId}/comments/{commentId}", publishedId, commentId), TestTokens.admin(admin)))
                .andExpect(status().isForbidden());
        assertThat(commentRepository.existsById(commentId)).isTrue();
        assertThat(commentRepository.existsById(replyId)).isTrue();

        mockMvc.perform(as(delete("/api/posts/{postId}/comments/{commentId}", publishedId, replyId), TestTokens.student(author)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(delete("/api/posts/{postId}/comments/{commentId}", publishedId, commentId), TestTokens.student(author)))
                .andExpect(status().isNoContent());
        assertThat(commentRepository.existsById(commentId)).isFalse();
    }

    @Test
    void commentingRequiresACommunityRoleAndAPublishedPost() throws Exception {
        mockMvc.perform(as(post("/api/posts/{id}/comments", publishedId), TestTokens.user(UUID.randomUUID(), "ROLE_PENDING_ACADEMICIAN"))
                        .contentType(MediaType.APPLICATION_JSON).content(COMMENT_BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/posts/{id}/comments", pendingId), TestTokens.student(otherStudent))
                        .contentType(MediaType.APPLICATION_JSON).content(COMMENT_BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(post("/api/posts/{id}/comments", pendingId), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content(COMMENT_BODY))
                .andExpect(status().isBadRequest());
        assertThat(commentRepository.countByPostIdAndStatus(pendingId, CommentStatus.PUBLISHED)).isZero();

        mockMvc.perform(as(post("/api/posts/{id}/comments", publishedId), TestTokens.student(otherStudent))
                        .contentType(MediaType.APPLICATION_JSON).content(COMMENT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(otherStudent.toString()));
        mockMvc.perform(as(post("/api/posts/comments/{id}/replies", commentId), TestTokens.student(otherStudent))
                        .contentType(MediaType.APPLICATION_JSON).content(COMMENT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorId").value(otherStudent.toString()));
        mockMvc.perform(as(post("/api/posts/{id}/comments", publishedId), TestTokens.academician(academician))
                        .contentType(MediaType.APPLICATION_JSON).content(COMMENT_BODY))
                .andExpect(status().isCreated());
        mockMvc.perform(as(post("/api/posts/comments/{id}/replies", commentId), TestTokens.admin(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(COMMENT_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void unpublishedPostsAreVisibleOnlyToTheirAuthor() throws Exception {
        mockMvc.perform(as(get("/api/posts/{id}", pendingId), TestTokens.student(otherStudent)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_NOT_FOUND"));
        mockMvc.perform(as(get("/api/posts/{id}", rejectedId), TestTokens.student(otherStudent)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_NOT_FOUND"));
        mockMvc.perform(as(get("/api/posts/{id}", publishedId), TestTokens.student(otherStudent)))
                .andExpect(status().isOk());

        mockMvc.perform(as(get("/api/posts/{id}", pendingId), TestTokens.student(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        mockMvc.perform(as(get("/api/posts/{id}", rejectedId), TestTokens.student(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void feedsNeverListSomeoneElsesUnpublishedPosts() throws Exception {
        mockMvc.perform(as(get("/api/posts").param("size", "200"), TestTokens.student(otherStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == '" + publishedId + "')]").exists())
                .andExpect(jsonPath("$.content[?(@.id == '" + pendingId + "')]").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.id == '" + rejectedId + "')]").doesNotExist());
        mockMvc.perform(as(get("/api/posts/me"), TestTokens.student(otherStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(as(get("/api/posts/me"), TestTokens.student(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    void commentsOfAnUnpublishedPostAreHiddenFromOtherUsers() throws Exception {
        UUID hiddenComment = saveComment(pendingId, author, null);

        mockMvc.perform(as(get("/api/posts/{id}/comments", pendingId), TestTokens.student(otherStudent)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/posts/comments/{id}/replies", hiddenComment), TestTokens.student(otherStudent)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(get("/api/posts/{id}/comments", pendingId), TestTokens.student(author)))
                .andExpect(status().isOk());
        mockMvc.perform(as(get("/api/posts/comments/{id}/replies", hiddenComment), TestTokens.student(author)))
                .andExpect(status().isOk());
    }

    @Test
    void unpublishedPostsCannotBeBookmarkedByOthers() throws Exception {
        mockMvc.perform(as(post("/api/posts/{id}/bookmark", pendingId), TestTokens.student(otherStudent)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("POST_NOT_FOUND"));
        mockMvc.perform(as(post("/api/posts/{id}/bookmark", pendingId), TestTokens.student(author)))
                .andExpect(status().isOk());
    }

    @Test
    void repliesCannotBeAddedToCommentsOfAnUnpublishedPost() throws Exception {
        UUID hiddenComment = saveComment(pendingId, author, null);

        mockMvc.perform(as(post("/api/posts/comments/{id}/replies", hiddenComment), TestTokens.student(otherStudent))
                        .contentType(MediaType.APPLICATION_JSON).content(COMMENT_BODY))
                .andExpect(status().is4xxClientError());
        assertThat(commentRepository.findByParentCommentIdAndStatus(hiddenComment, CommentStatus.PUBLISHED)).isEmpty();
    }

    @Test
    void likesNeedACommunityRoleAndAPublishedPost() throws Exception {
        mockMvc.perform(as(put("/api/posts/{id}/likes", publishedId), TestTokens.user(UUID.randomUUID(), "ROLE_PENDING_STUDENT")))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(put("/api/posts/{id}/likes", pendingId), TestTokens.student(otherStudent)))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(put("/api/posts/{id}/likes", pendingId), TestTokens.student(author)))
                .andExpect(status().isBadRequest());
        assertThat(postLikeRepository.countByPostId(publishedId)).isZero();
        assertThat(postLikeRepository.countByPostId(pendingId)).isZero();

        mockMvc.perform(as(put("/api/posts/{id}/likes", publishedId), TestTokens.student(otherStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));
        mockMvc.perform(as(delete("/api/posts/{id}/likes", publishedId), TestTokens.student(author)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1));
        assertThat(postLikeRepository.existsByPostIdAndUserId(publishedId, otherStudent)).isTrue();
    }

    @Test
    void moderationDecisionIsAcceptedOnlyFromServices() throws Exception {
        String path = "/api/posts/internal/{id}/moderation";
        String body = "{\"decision\":\"TEMIZ\"}";

        mockMvc.perform(put(path, pendingId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        mockMvc.perform(as(put(path, pendingId), TestTokens.student(author)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
        mockMvc.perform(as(put(path, pendingId), TestTokens.admin(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(put(path, pendingId), TestTokens.user(author, "ROLE_STUDENT,ROLE_SERVICE"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        assertThat(postRepository.findById(pendingId).orElseThrow().getStatus()).isEqualTo(PostStatus.PENDING);

        mockMvc.perform(as(put(path, pendingId), TestTokens.service("llm-service")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());
        assertThat(postRepository.findById(pendingId).orElseThrow().getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void recentPostsInternalEndpointAcceptsOnlyServiceTokens() throws Exception {
        String path = "/api/posts/internal/users/{id}/recent";
        mockMvc.perform(get(path, author))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(get(path, author), TestTokens.student(author)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get(path, author), TestTokens.service("user-service")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(publishedId.toString()));
    }

    @Test
    void userRoutesWithoutAVerifiedUserTokenAreUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/posts")
                        .header("X-Authenticated-User-Id", author.toString())
                        .header("X-Authenticated-User-Roles", "ROLE_STUDENT"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/posts/{id}", publishedId)
                        .header("X-Authenticated-User-Id", author.toString())
                        .header("X-Authenticated-User-Roles", "ROLE_STUDENT"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(delete("/api/posts/{id}", publishedId), TestTokens.service("llm-service")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/posts").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        assertThat(postRepository.existsById(publishedId)).isTrue();
    }

    private UUID savePost(PostStatus status) {
        Post post = new Post();
        post.setTitle("Başlık");
        post.setContent("İçerik");
        post.setCategory(PostCategory.SORU);
        post.setStatus(status);
        post.setAuthorId(author);
        return postRepository.save(post).getId();
    }

    private UUID saveComment(UUID postId, UUID authorId, UUID parentId) {
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setParentCommentId(parentId);
        comment.setContent("Yorum");
        comment.setStatus(CommentStatus.PUBLISHED);
        return commentRepository.save(comment).getId();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
