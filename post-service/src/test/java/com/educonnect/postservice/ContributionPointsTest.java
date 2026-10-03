package com.educonnect.postservice;

import com.educonnect.common.test.TestTokens;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostIntegrationTest
class ContributionPointsTest {

    private final UUID author = UUID.randomUUID();
    private final UUID reader = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void notesEarnPointsWhenOtherPeopleLikeOrSaveThem() throws Exception {
        UUID note = savePost(PostCategory.DERS_NOTU);
        UUID question = savePost(PostCategory.SORU);

        mockMvc.perform(as(put("/api/posts/{id}/likes", note), TestTokens.student(reader))).andExpect(status().isOk());
        mockMvc.perform(as(put("/api/posts/{id}/likes", note), TestTokens.student(author))).andExpect(status().isOk());
        mockMvc.perform(as(put("/api/posts/{id}/likes", question), TestTokens.student(reader))).andExpect(status().isOk());
        mockMvc.perform(as(post("/api/posts/{id}/bookmark", note), TestTokens.academician(UUID.randomUUID()))).andExpect(status().isOk());

        assertThat(events("gamification.note.appreciated", note + ":" + reader)).isEqualTo(1);
        assertThat(events("gamification.note.appreciated", note + ":" + author)).isZero();
        assertThat(events("gamification.note.appreciated", question.toString())).isZero();
        assertThat(events("gamification.note.appreciated", "NOTE_SAVED", note.toString())).isEqualTo(1);
        assertThat(events("gamification.note.appreciated", "\"contentId\":\"" + note + "\"")).isEqualTo(2);
    }

    @Test
    void questionAuthorsAcceptOneAnswerAndSwitchingMovesThePoints() throws Exception {
        UUID question = savePost(PostCategory.SORU);
        UUID first = saveComment(question, reader);
        UUID secondAuthor = UUID.randomUUID();
        UUID second = saveComment(question, secondAuthor);
        UUID own = saveComment(question, author);

        mockMvc.perform(as(put("/api/posts/{id}/accepted-answer", question), TestTokens.student(reader))
                        .contentType(MediaType.APPLICATION_JSON).content(answer(first)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(put("/api/posts/{id}/accepted-answer", question), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content(answer(own)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CANNOT_ACCEPT_OWN"));
        mockMvc.perform(as(put("/api/posts/{id}/accepted-answer", question), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content(answer(first)))
                .andExpect(status().isNoContent());
        assertThat(postRepository.findById(question).orElseThrow().getAcceptedCommentId()).isEqualTo(first);
        assertThat(events("gamification.answer.accepted", first.toString())).isEqualTo(1);

        mockMvc.perform(as(put("/api/posts/{id}/accepted-answer", question), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content(answer(second)))
                .andExpect(status().isNoContent());
        assertThat(events("gamification.content.revised", "POINTS_REVERSED", "revoke:" + first)).isEqualTo(1);
        assertThat(events("gamification.answer.accepted", second.toString())).isEqualTo(1);

        mockMvc.perform(as(delete("/api/posts/{id}/accepted-answer", question), TestTokens.student(author)))
                .andExpect(status().isNoContent());
        assertThat(events("gamification.content.revised", "POINTS_REVERSED", "revoke:" + second)).isEqualTo(1);
        mockMvc.perform(as(delete("/api/posts/{id}/accepted-answer", question), TestTokens.student(author)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("NO_ACCEPTED_ANSWER"));

        UUID note = savePost(PostCategory.DERS_NOTU);
        UUID noteComment = saveComment(note, reader);
        mockMvc.perform(as(put("/api/posts/{id}/accepted-answer", note), TestTokens.student(author))
                        .contentType(MediaType.APPLICATION_JSON).content(answer(noteComment)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("NOT_A_QUESTION"));
    }

    @Test
    void removedContentLosesItsPointsAndRestoredContentRegainsThem() throws Exception {
        UUID note = savePost(PostCategory.DERS_NOTU);
        String moderator = TestTokens.user(UUID.randomUUID(), "ROLE_STAFF,PERM_MODERATOR");

        mockMvc.perform(as(post("/api/posts/moderation/posts/{id}/remove", note), moderator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Sınav sorusu\"}"))
                .andExpect(status().isNoContent());
        assertThat(events("gamification.content.revised", "POINTS_REVERSED", "revoke:" + note)).isEqualTo(1);

        mockMvc.perform(as(post("/api/posts/moderation/posts/{id}/restore", note), moderator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Kendi özeti\"}"))
                .andExpect(status().isNoContent());
        assertThat(events("gamification.content.revised", "POINTS_RESTORED", "restore:" + note)).isEqualTo(1);

        mockMvc.perform(as(delete("/api/posts/{id}", note), TestTokens.student(author))).andExpect(status().isNoContent());
        assertThat(events("gamification.content.revised", "POINTS_REVERSED", "revoke:" + note)).isEqualTo(2);
        assertThat(events("gamification.content.revised", "\"contentId\":\"" + note + "\"")).isEqualTo(3);
    }

    private int events(String routingKey, String... fragments) {
        StringBuilder sql = new StringBuilder("select count(*) from outbox_messages where routing_key = ?");
        Object[] args = new Object[fragments.length + 1];
        args[0] = routingKey;
        for (int i = 0; i < fragments.length; i++) {
            sql.append(" and convert_from(body, 'UTF8') like ?");
            args[i + 1] = "%" + fragments[i] + "%";
        }
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args);
        return count == null ? 0 : count;
    }

    private static String answer(UUID commentId) {
        return "{\"commentId\":\"" + commentId + "\"}";
    }

    private UUID savePost(PostCategory category) {
        Post post = new Post();
        post.setTitle("Başlık");
        post.setContent("İçerik");
        post.setCategory(category);
        post.setStatus(PostStatus.PUBLISHED);
        post.setAuthorId(author);
        post.setSubmittedAt(Instant.now());
        return postRepository.save(post).getId();
    }

    private UUID saveComment(UUID postId, UUID commentAuthor) {
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(commentAuthor);
        comment.setContent("Cevap");
        comment.setStatus(CommentStatus.PUBLISHED);
        return commentRepository.save(comment).getId();
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
