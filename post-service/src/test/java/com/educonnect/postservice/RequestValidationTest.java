package com.educonnect.postservice;

import com.educonnect.postservice.dto.CreateCommentRequest;
import com.educonnect.postservice.dto.CreatePostRequest;
import com.educonnect.postservice.dto.ModerationDecisionRequest;
import com.educonnect.postservice.dto.UpdatePostRequest;
import com.educonnect.postservice.model.PostCategory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static final PostCategory CATEGORY = PostCategory.values()[0];

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createPostRequestAcceptsValidSample() {
        assertThat(validator.validate(new CreatePostRequest("Başlık", "İçerik", CATEGORY))).isEmpty();
    }

    @Test
    void createPostRequestRejectsBlankTitle() {
        assertThat(paths(validator.validate(new CreatePostRequest(" ", "İçerik", CATEGORY)))).containsExactly("title");
    }

    @Test
    void createPostRequestRejectsTooLongTitle() {
        assertThat(paths(validator.validate(new CreatePostRequest("a".repeat(256), "İçerik", CATEGORY))))
                .containsExactly("title");
    }

    @Test
    void createPostRequestRejectsBlankContent() {
        assertThat(paths(validator.validate(new CreatePostRequest("Başlık", "", CATEGORY)))).containsExactly("content");
    }

    @Test
    void createPostRequestRejectsMissingCategory() {
        assertThat(paths(validator.validate(new CreatePostRequest("Başlık", "İçerik", null)))).containsExactly("category");
    }

    @Test
    void updatePostRequestAcceptsValidSample() {
        assertThat(validator.validate(new UpdatePostRequest("Başlık", "İçerik", CATEGORY))).isEmpty();
    }

    @Test
    void updatePostRequestRejectsBlankTitle() {
        assertThat(paths(validator.validate(new UpdatePostRequest(null, "İçerik", CATEGORY)))).containsExactly("title");
    }

    @Test
    void updatePostRequestRejectsTooLongTitle() {
        assertThat(paths(validator.validate(new UpdatePostRequest("a".repeat(256), "İçerik", CATEGORY))))
                .containsExactly("title");
    }

    @Test
    void updatePostRequestRejectsBlankContent() {
        assertThat(paths(validator.validate(new UpdatePostRequest("Başlık", " ", CATEGORY)))).containsExactly("content");
    }

    @Test
    void updatePostRequestRejectsMissingCategory() {
        assertThat(paths(validator.validate(new UpdatePostRequest("Başlık", "İçerik", null)))).containsExactly("category");
    }

    @Test
    void createCommentRequestAcceptsValidSample() {
        assertThat(validator.validate(new CreateCommentRequest("Yorum", UUID.randomUUID()))).isEmpty();
        assertThat(validator.validate(new CreateCommentRequest("Yorum", null))).isEmpty();
    }

    @Test
    void createCommentRequestRejectsBlankContent() {
        assertThat(paths(validator.validate(new CreateCommentRequest(" ", null)))).containsExactly("content");
    }

    @Test
    void createCommentRequestRejectsTooLongContent() {
        assertThat(paths(validator.validate(new CreateCommentRequest("a".repeat(2001), null)))).containsExactly("content");
    }

    @Test
    void moderationDecisionRequestAcceptsValidSample() {
        assertThat(validator.validate(new ModerationDecisionRequest("TEMIZ", UUID.randomUUID().toString()))).isEmpty();
        assertThat(validator.validate(new ModerationDecisionRequest("ZORBA", null))).isEmpty();
    }

    @Test
    void moderationDecisionRequestRejectsBlankDecision() {
        assertThat(paths(validator.validate(new ModerationDecisionRequest(" ", null)))).containsExactly("decision");
    }

    private static List<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath().toString()).toList();
    }
}
