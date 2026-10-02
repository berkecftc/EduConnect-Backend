package com.educonnect.postservice.service;

import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.ApiException;
import com.educonnect.postservice.config.RabbitMQConfig;
import com.educonnect.postservice.event.ActionType;
import com.educonnect.postservice.event.GamificationEvent;
import com.educonnect.postservice.exception.CommentNotFoundException;
import com.educonnect.postservice.exception.PostNotFoundException;
import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.CommentStatus;
import com.educonnect.postservice.model.ContentReport;
import com.educonnect.postservice.model.ModerationAction;
import com.educonnect.postservice.model.ModerationActor;
import com.educonnect.postservice.model.ModerationTarget;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.ContentReportRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class ContentControlService {

    private static final Logger log = LoggerFactory.getLogger(ContentControlService.class);

    static final String REPORT_THRESHOLD_FLAG = "Birden fazla şikâyet aldı; moderatör incelemesine kadar gizlendi.";

    private static final Set<PostStatus> REMOVABLE_POSTS = Set.of(PostStatus.PUBLISHED, PostStatus.HIDDEN, PostStatus.IN_REVIEW);
    private static final Set<CommentStatus> REMOVABLE_COMMENTS = Set.of(CommentStatus.PUBLISHED, CommentStatus.HIDDEN, CommentStatus.IN_REVIEW);
    private static final Set<PostStatus> RESTORABLE_POSTS = Set.of(PostStatus.HIDDEN, PostStatus.REMOVED);
    private static final Set<CommentStatus> RESTORABLE_COMMENTS = Set.of(CommentStatus.HIDDEN, CommentStatus.REMOVED);

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ContentReportRepository reportRepository;
    private final PublisherPolicy publisherPolicy;
    private final ModerationLog moderationLog;
    private final OutboxPublisher outboxPublisher;
    private final ContributionEvents contributionEvents;

    public ContentControlService(PostRepository postRepository,
                                 CommentRepository commentRepository,
                                 ContentReportRepository reportRepository,
                                 PublisherPolicy publisherPolicy,
                                 ModerationLog moderationLog,
                                 OutboxPublisher outboxPublisher,
                                 ContributionEvents contributionEvents) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.reportRepository = reportRepository;
        this.publisherPolicy = publisherPolicy;
        this.moderationLog = moderationLog;
        this.outboxPublisher = outboxPublisher;
        this.contributionEvents = contributionEvents;
    }

    @Transactional
    public void hidePost(UUID postId, String reason, Viewer viewer) {
        Post post = findPost(postId);
        ModerationActor actor = viewer.moderator() ? ModerationActor.MODERATOR : requirePostScopeOwner(viewer, post);
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_PUBLISHED", "Yalnız yayındaki içerik gizlenebilir.");
        }
        hide(post, actor, viewer.id(), reason.strip());
    }

    @Transactional
    public void hideComment(UUID commentId, String reason, Viewer viewer) {
        Comment comment = findComment(commentId);
        Post post = findPost(comment.getPostId());
        ModerationActor actor = viewer.moderator() ? ModerationActor.MODERATOR : requireCommentScopeOwner(viewer, post);
        if (comment.getStatus() != CommentStatus.PUBLISHED) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_PUBLISHED", "Yalnız yayındaki içerik gizlenebilir.");
        }
        hide(comment, actor, viewer.id(), reason.strip());
    }

    @Transactional
    public void removePost(UUID postId, String reason, Viewer moderator) {
        moderator.requireModerator();
        Post post = findPost(postId);
        if (!REMOVABLE_POSTS.contains(post.getStatus())) {
            throw notApplicable();
        }
        post.setStatus(PostStatus.REMOVED);
        post.setModerationFlag(null);
        post.setReviewNote(reason.strip());
        postRepository.save(post);
        moderationLog.record(ModerationTarget.POST, post.getId(), post.getId(), ModerationAction.REMOVED,
                ModerationActor.MODERATOR, moderator.id(), reason.strip());
        upholdReports(ModerationTarget.POST, post.getId(), moderator.id(), reason.strip());
        contributionEvents.revoke(post.getAuthorId(), post.getId());
        log.warn("Post removed by a moderator. postId={}, moderator={}", postId, moderator.id());
    }

    @Transactional
    public void removeComment(UUID commentId, String reason, Viewer moderator) {
        moderator.requireModerator();
        Comment comment = findComment(commentId);
        if (!REMOVABLE_COMMENTS.contains(comment.getStatus())) {
            throw notApplicable();
        }
        comment.setStatus(CommentStatus.REMOVED);
        comment.setModerationFlag(null);
        comment.setModerationNote(reason.strip());
        commentRepository.save(comment);
        moderationLog.record(ModerationTarget.COMMENT, comment.getId(), comment.getPostId(), ModerationAction.REMOVED,
                ModerationActor.MODERATOR, moderator.id(), reason.strip());
        upholdReports(ModerationTarget.COMMENT, comment.getId(), moderator.id(), reason.strip());
        contributionEvents.revoke(comment.getAuthorId(), comment.getId());
        log.warn("Comment removed by a moderator. commentId={}, moderator={}", commentId, moderator.id());
    }

    @Transactional
    public void restorePost(UUID postId, String reason, Viewer moderator) {
        moderator.requireModerator();
        Post post = findPost(postId);
        if (!RESTORABLE_POSTS.contains(post.getStatus())) {
            throw notApplicable();
        }
        post.setStatus(PostStatus.PUBLISHED);
        post.setModerationFlag(null);
        post.setReviewNote(null);
        postRepository.save(post);
        moderationLog.record(ModerationTarget.POST, post.getId(), post.getId(), ModerationAction.RESTORED,
                ModerationActor.MODERATOR, moderator.id(), clean(reason));
        dismissReports(ModerationTarget.POST, post.getId(), moderator.id(), clean(reason));
        contributionEvents.restore(post.getAuthorId(), post.getId());
    }

    @Transactional
    public void restoreComment(UUID commentId, String reason, Viewer moderator) {
        moderator.requireModerator();
        Comment comment = findComment(commentId);
        if (!RESTORABLE_COMMENTS.contains(comment.getStatus())) {
            throw notApplicable();
        }
        comment.setStatus(CommentStatus.PUBLISHED);
        comment.setModerationFlag(null);
        comment.setModerationNote(null);
        commentRepository.save(comment);
        moderationLog.record(ModerationTarget.COMMENT, comment.getId(), comment.getPostId(), ModerationAction.RESTORED,
                ModerationActor.MODERATOR, moderator.id(), clean(reason));
        dismissReports(ModerationTarget.COMMENT, comment.getId(), moderator.id(), clean(reason));
        contributionEvents.restore(comment.getAuthorId(), comment.getId());
    }

    void hideForReports(ModerationTarget target, UUID targetId) {
        if (target == ModerationTarget.POST) {
            postRepository.findById(targetId)
                    .filter(post -> post.getStatus() == PostStatus.PUBLISHED)
                    .ifPresent(post -> {
                        hide(post, ModerationActor.AUTOMATIC, null, REPORT_THRESHOLD_FLAG);
                        post.setModerationFlag(REPORT_THRESHOLD_FLAG);
                    });
        } else {
            commentRepository.findById(targetId)
                    .filter(comment -> comment.getStatus() == CommentStatus.PUBLISHED)
                    .ifPresent(comment -> {
                        hide(comment, ModerationActor.AUTOMATIC, null, REPORT_THRESHOLD_FLAG);
                        comment.setModerationFlag(REPORT_THRESHOLD_FLAG);
                    });
        }
    }

    private void hide(Post post, ModerationActor actor, UUID actorId, String reason) {
        post.setStatus(PostStatus.HIDDEN);
        post.setReviewNote(reason);
        postRepository.save(post);
        moderationLog.record(ModerationTarget.POST, post.getId(), post.getId(), ModerationAction.HIDDEN, actor, actorId, reason);
        contributionEvents.revoke(post.getAuthorId(), post.getId());
        log.info("Post hidden. postId={}, actor={}", post.getId(), actor);
    }

    private void hide(Comment comment, ModerationActor actor, UUID actorId, String reason) {
        comment.setStatus(CommentStatus.HIDDEN);
        comment.setModerationNote(reason);
        commentRepository.save(comment);
        moderationLog.record(ModerationTarget.COMMENT, comment.getId(), comment.getPostId(), ModerationAction.HIDDEN,
                actor, actorId, reason);
        contributionEvents.revoke(comment.getAuthorId(), comment.getId());
        log.info("Comment hidden. commentId={}, actor={}", comment.getId(), actor);
    }

    private ModerationActor requirePostScopeOwner(Viewer viewer, Post post) {
        if (publisherPolicy.ownsScope(viewer, post)) {
            return ModerationActor.SCOPE_OWNER;
        }
        throw notScopeOwner();
    }

    private ModerationActor requireCommentScopeOwner(Viewer viewer, Post post) {
        if (viewer.id().equals(post.getAuthorId()) || publisherPolicy.ownsScope(viewer, post)) {
            return ModerationActor.SCOPE_OWNER;
        }
        throw notScopeOwner();
    }

    private void upholdReports(ModerationTarget target, UUID targetId, UUID moderatorId, String note) {
        Instant now = Instant.now();
        for (ContentReport report : reportRepository.findByTargetTypeAndTargetIdAndStatus(target, targetId, ContentReport.Status.OPEN)) {
            report.resolve(ContentReport.Status.UPHELD, moderatorId, note, now);
            reportRepository.save(report);
            if (report.getReporterId() != null) {
                outboxPublisher.publish(RabbitMQConfig.GAMIFICATION_EXCHANGE,
                        RabbitMQConfig.ROUTING_KEY_GAMIFICATION_REPORT_RESOLVED,
                        new GamificationEvent(report.getReporterId(), ActionType.VALID_REPORT,
                                report.getId().toString(), OffsetDateTime.now()));
            }
        }
    }

    void dismissReports(ModerationTarget target, UUID targetId, UUID moderatorId, String note) {
        Instant now = Instant.now();
        for (ContentReport report : reportRepository.findByTargetTypeAndTargetIdAndStatus(target, targetId, ContentReport.Status.OPEN)) {
            report.resolve(ContentReport.Status.DISMISSED, moderatorId, note, now);
            reportRepository.save(report);
        }
    }

    private Post findPost(UUID postId) {
        return postRepository.findById(postId).orElseThrow(() -> new PostNotFoundException("Post bulunamadı: " + postId));
    }

    private Comment findComment(UUID commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("Yorum bulunamadı: " + commentId));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static ApiException notApplicable() {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_CONTENT_STATE", "Bu işlem içeriğin şu anki durumunda yapılamaz.");
    }

    private static ApiException notScopeOwner() {
        return new ApiException(HttpStatus.FORBIDDEN, "NOT_SCOPE_OWNER",
                "Bu içeriği yalnız sorumlusu (gönderi sahibi, kulüp başkanı veya danışmanı, dersin hocası) ya da moderatör gizleyebilir.");
    }
}
