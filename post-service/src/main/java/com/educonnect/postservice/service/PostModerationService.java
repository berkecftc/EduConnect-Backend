package com.educonnect.postservice.service;

import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.ApiException;
import com.educonnect.postservice.config.RabbitMQConfig;
import com.educonnect.postservice.dto.ModerationDecision;
import com.educonnect.postservice.dto.ModeratorDecisionRequest;
import com.educonnect.postservice.event.ActionType;
import com.educonnect.postservice.event.GamificationEvent;
import com.educonnect.postservice.event.PostModerationEvent;
import com.educonnect.postservice.exception.CommentNotFoundException;
import com.educonnect.postservice.exception.PostNotFoundException;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PostModerationService {

    private static final Logger log = LoggerFactory.getLogger(PostModerationService.class);

    static final String POST_REJECTED = "Gönderi topluluk kurallarına aykırı ifade içeriyor.";
    static final String COMMENT_REJECTED = "Yorum topluluk kurallarına aykırı ifade içeriyor.";
    static final String FLAG_SUSPECTED = "Otomatik kontrol zorbalık veya hakaret şüphesi bildirdi.";
    static final String FLAG_UNDECIDED = "Otomatik kontrol karar veremedi.";
    static final String FLAG_TIMEOUT = "Otomatik kontrol zamanında tamamlanmadı.";

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ModerationRecordRepository recordRepository;
    private final OutboxPublisher outboxPublisher;

    public PostModerationService(PostRepository postRepository,
                                 CommentRepository commentRepository,
                                 ModerationRecordRepository recordRepository,
                                 OutboxPublisher outboxPublisher) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.recordRepository = recordRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public void applyModeration(UUID postId, ModerationDecision decision, boolean wordList, UUID eventId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            log.warn("Post not found during moderation. postId={}, eventId={}", postId, eventId);
            return;
        }
        if (post.getStatus() != PostStatus.PENDING) {
            log.info("Post already moderated (status={}). postId={}, eventId={}", post.getStatus(), postId, eventId);
            return;
        }
        switch (decision) {
            case TEMIZ -> publish(post, ModerationActor.AUTOMATIC, null, null);
            case ZORBA -> {
                if (wordList) {
                    reject(post, ModerationActor.AUTOMATIC, null, POST_REJECTED);
                } else {
                    sendToReview(post, FLAG_SUSPECTED);
                }
            }
            case INCELEME -> sendToReview(post, FLAG_UNDECIDED);
        }
    }

    @Transactional
    public void applyCommentModeration(UUID commentId, ModerationDecision decision, boolean wordList, UUID eventId) {
        Comment comment = commentRepository.findById(commentId).orElse(null);
        if (comment == null) {
            log.warn("Comment not found during moderation. commentId={}, eventId={}", commentId, eventId);
            return;
        }
        if (comment.getStatus() != CommentStatus.PENDING) {
            log.info("Comment already moderated (status={}). commentId={}, eventId={}", comment.getStatus(), commentId, eventId);
            return;
        }
        switch (decision) {
            case TEMIZ -> publish(comment, ModerationActor.AUTOMATIC, null, null);
            case ZORBA -> {
                if (wordList) {
                    reject(comment, ModerationActor.AUTOMATIC, null, COMMENT_REJECTED);
                } else {
                    sendToReview(comment, FLAG_SUSPECTED);
                }
            }
            case INCELEME -> sendToReview(comment, FLAG_UNDECIDED);
        }
    }

    @Transactional
    public void markUndecided(PostModerationEvent event) {
        if (event.comment()) {
            commentRepository.findById(event.getCommentId())
                    .filter(comment -> comment.getStatus() == CommentStatus.PENDING)
                    .ifPresent(comment -> sendToReview(comment, FLAG_UNDECIDED));
        } else if (event.getPostId() != null) {
            postRepository.findById(event.getPostId())
                    .filter(post -> post.getStatus() == PostStatus.PENDING)
                    .ifPresent(post -> sendToReview(post, FLAG_UNDECIDED));
        }
    }

    @Transactional
    public int escalateStale(Instant cutoff) {
        int escalated = 0;
        for (Post post : postRepository.findByStatusAndSubmittedAtBefore(PostStatus.PENDING, cutoff)) {
            sendToReview(post, FLAG_TIMEOUT);
            escalated++;
        }
        for (Comment comment : commentRepository.findByStatusAndSubmittedAtBefore(CommentStatus.PENDING, cutoff)) {
            sendToReview(comment, FLAG_TIMEOUT);
            escalated++;
        }
        if (escalated > 0) {
            log.warn("{} content item(s) waited too long for automatic moderation and were sent to the moderator queue", escalated);
        }
        return escalated;
    }

    @Transactional
    public void decidePost(UUID postId, ModeratorDecisionRequest request, Viewer moderator) {
        moderator.requireModerator();
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post bulunamadı: " + postId));
        if (post.getStatus() != PostStatus.IN_REVIEW && post.getStatus() != PostStatus.PENDING) {
            throw notReviewable();
        }
        if (request.action() == ModeratorDecisionRequest.Action.APPROVE) {
            publish(post, ModerationActor.MODERATOR, moderator.id(), clean(request.reason()));
        } else {
            reject(post, ModerationActor.MODERATOR, moderator.id(), requireReason(request.reason()));
        }
    }

    @Transactional
    public void decideComment(UUID commentId, ModeratorDecisionRequest request, Viewer moderator) {
        moderator.requireModerator();
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("Yorum bulunamadı: " + commentId));
        if (comment.getStatus() != CommentStatus.IN_REVIEW && comment.getStatus() != CommentStatus.PENDING) {
            throw notReviewable();
        }
        if (request.action() == ModeratorDecisionRequest.Action.APPROVE) {
            publish(comment, ModerationActor.MODERATOR, moderator.id(), clean(request.reason()));
        } else {
            reject(comment, ModerationActor.MODERATOR, moderator.id(), requireReason(request.reason()));
        }
    }

    private void publish(Post post, ModerationActor actor, UUID actorId, String reason) {
        post.setStatus(PostStatus.PUBLISHED);
        post.setModerationFlag(null);
        post.setReviewNote(null);
        postRepository.save(post);
        record(ModerationTarget.POST, post.getId(), post.getId(), ModerationAction.PUBLISHED, actor, actorId, reason);
        log.info("Post published by moderation. postId={}, actor={}", post.getId(), actor);
        if (post.getCategory() == PostCategory.DERS_NOTU && post.getAuthorId() != null) {
            publishGamificationEvent(post);
        }
    }

    private void reject(Post post, ModerationActor actor, UUID actorId, String reason) {
        post.setStatus(PostStatus.REJECTED);
        post.setModerationFlag(null);
        post.setReviewNote(reason);
        postRepository.save(post);
        record(ModerationTarget.POST, post.getId(), post.getId(), ModerationAction.REJECTED, actor, actorId, reason);
        log.warn("Post rejected by moderation. postId={}, actor={}", post.getId(), actor);
    }

    private void sendToReview(Post post, String flag) {
        post.setStatus(PostStatus.IN_REVIEW);
        post.setModerationFlag(flag);
        postRepository.save(post);
        record(ModerationTarget.POST, post.getId(), post.getId(), ModerationAction.SENT_TO_REVIEW,
                ModerationActor.AUTOMATIC, null, flag);
        log.info("Post sent to the moderator queue. postId={}", post.getId());
    }

    private void publish(Comment comment, ModerationActor actor, UUID actorId, String reason) {
        comment.setStatus(CommentStatus.PUBLISHED);
        comment.setModerationFlag(null);
        comment.setModerationNote(null);
        commentRepository.save(comment);
        record(ModerationTarget.COMMENT, comment.getId(), comment.getPostId(), ModerationAction.PUBLISHED, actor, actorId, reason);
    }

    private void reject(Comment comment, ModerationActor actor, UUID actorId, String reason) {
        comment.setStatus(CommentStatus.REJECTED);
        comment.setModerationFlag(null);
        comment.setModerationNote(reason);
        commentRepository.save(comment);
        record(ModerationTarget.COMMENT, comment.getId(), comment.getPostId(), ModerationAction.REJECTED, actor, actorId, reason);
        log.warn("Comment rejected by moderation. commentId={}, actor={}", comment.getId(), actor);
    }

    private void sendToReview(Comment comment, String flag) {
        comment.setStatus(CommentStatus.IN_REVIEW);
        comment.setModerationFlag(flag);
        commentRepository.save(comment);
        record(ModerationTarget.COMMENT, comment.getId(), comment.getPostId(), ModerationAction.SENT_TO_REVIEW,
                ModerationActor.AUTOMATIC, null, flag);
    }

    private void record(ModerationTarget target, UUID targetId, UUID postId, ModerationAction action,
                        ModerationActor actor, UUID actorId, String reason) {
        recordRepository.save(new ModerationRecord(target, targetId, postId, action, actor, actorId, reason, Instant.now()));
    }

    private static String requireReason(String reason) {
        String cleaned = clean(reason);
        if (cleaned == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REASON_REQUIRED", "Reddetme gerekçesi zorunludur.");
        }
        return cleaned;
    }

    private static String clean(String reason) {
        return reason == null || reason.isBlank() ? null : reason.strip();
    }

    private static ApiException notReviewable() {
        return new ApiException(HttpStatus.CONFLICT, "NOT_IN_REVIEW", "Bu içerik moderasyon beklemiyor.");
    }

    private void publishGamificationEvent(Post post) {
        GamificationEvent gamificationEvent = new GamificationEvent(
                post.getAuthorId(),
                ActionType.POST_PUBLISHED,
                post.getId().toString(),
                OffsetDateTime.now()
        );
        outboxPublisher.publish(
                RabbitMQConfig.GAMIFICATION_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_GAMIFICATION_POST_PUBLISHED,
                gamificationEvent
        );
        log.info("Gamification event queued. postId={}", post.getId());
    }
}
