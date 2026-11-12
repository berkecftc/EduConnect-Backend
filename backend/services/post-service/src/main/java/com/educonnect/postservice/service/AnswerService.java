package com.educonnect.postservice.service;

import com.educonnect.common.web.ApiException;
import com.educonnect.postservice.exception.CommentNotFoundException;
import com.educonnect.postservice.exception.PostNotFoundException;
import com.educonnect.postservice.exception.UnauthorizedPostAccessException;
import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.CommentStatus;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.educonnect.common.messaging.notification.NotificationCategory;

import java.util.UUID;
import java.util.Collections;

@Service
public class AnswerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerService.class);

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ContributionEvents contributionEvents;
    private final PostNotifier notifier;

    public AnswerService(PostRepository postRepository, CommentRepository commentRepository,
                         ContributionEvents contributionEvents,
                         PostNotifier notifier) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.contributionEvents = contributionEvents;
        this.notifier = notifier;
    }

    @Transactional
    public void accept(UUID postId, UUID commentId, Viewer viewer) {
        Post post = ownQuestion(postId, viewer);
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("Yorum bulunamadı: " + commentId));
        if (!comment.getPostId().equals(postId) || comment.getParentCommentId() != null
                || comment.getStatus() != CommentStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANSWER",
                    "Cevap olarak yalnız bu soruya yazılmış, yayındaki bir üst yorum seçilebilir.");
        }
        if (viewer.id().equals(comment.getAuthorId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_ACCEPT_OWN", "Kendi yorumunuzu cevap olarak seçemezsiniz.");
        }
        if (commentId.equals(post.getAcceptedCommentId())) {
            return;
        }
        revokeCurrent(post);
        post.setAcceptedCommentId(commentId);
        postRepository.save(post);
        contributionEvents.answerAccepted(comment.getAuthorId(), commentId);
        notifier.notify(Collections.singletonList(comment.getAuthorId()), NotificationCategory.COMMUNITY, "ANSWER_ACCEPTED", postId,
                "Cevabınız kabul edildi", "\"" + post.getTitle() + "\" sorusunda yazdığınız cevap, soru sahibi tarafından kabul edildi.",
                "answer:" + commentId);
        log.info("Answer accepted. postId={}, commentId={}", postId, commentId);
    }

    @Transactional
    public void unaccept(UUID postId, Viewer viewer) {
        Post post = ownQuestion(postId, viewer);
        if (post.getAcceptedCommentId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "NO_ACCEPTED_ANSWER", "Bu sorunun kabul edilmiş cevabı yok.");
        }
        revokeCurrent(post);
        post.setAcceptedCommentId(null);
        postRepository.save(post);
    }

    private void revokeCurrent(Post post) {
        if (post.getAcceptedCommentId() != null) {
            commentRepository.findById(post.getAcceptedCommentId())
                    .ifPresent(previous -> contributionEvents.revoke(previous.getAuthorId(), previous.getId()));
        }
    }

    private Post ownQuestion(UUID postId, Viewer viewer) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new PostNotFoundException("Post bulunamadı: " + postId));
        if (!viewer.id().equals(post.getAuthorId())) {
            throw new UnauthorizedPostAccessException("Cevabı yalnız soruyu soran seçebilir. postId: " + postId);
        }
        if (post.getCategory() != PostCategory.SORU) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_A_QUESTION", "Cevap yalnız soru gönderisinde seçilir.");
        }
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_PUBLISHED", "Soru yayında değil.");
        }
        return post;
    }
}
