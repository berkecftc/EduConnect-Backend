package com.educonnect.postservice.service;

import com.educonnect.common.web.ApiException;
import com.educonnect.postservice.dto.AppealDecisionRequest;
import com.educonnect.postservice.dto.AppealResponse;
import com.educonnect.postservice.exception.CommentNotFoundException;
import com.educonnect.postservice.exception.PostNotFoundException;
import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.CommentStatus;
import com.educonnect.postservice.model.ModerationAction;
import com.educonnect.postservice.model.ModerationActor;
import com.educonnect.postservice.model.ModerationAppeal;
import com.educonnect.postservice.model.ModerationRecord;
import com.educonnect.postservice.model.ModerationTarget;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.ModerationAppealRepository;
import com.educonnect.postservice.repository.ModerationRecordRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AppealService {

    private static final Logger log = LoggerFactory.getLogger(AppealService.class);

    private static final Map<PostStatus, ModerationAction> POST_APPEALABLE = Map.of(
            PostStatus.REJECTED, ModerationAction.REJECTED,
            PostStatus.HIDDEN, ModerationAction.HIDDEN,
            PostStatus.REMOVED, ModerationAction.REMOVED);
    private static final Map<CommentStatus, ModerationAction> COMMENT_APPEALABLE = Map.of(
            CommentStatus.REJECTED, ModerationAction.REJECTED,
            CommentStatus.HIDDEN, ModerationAction.HIDDEN,
            CommentStatus.REMOVED, ModerationAction.REMOVED);

    private final ModerationAppealRepository appealRepository;
    private final ModerationRecordRepository recordRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ContentControlService contentControl;
    private final ModerationLog moderationLog;
    private final ContributionEvents contributionEvents;

    public AppealService(ModerationAppealRepository appealRepository,
                         ModerationRecordRepository recordRepository,
                         PostRepository postRepository,
                         CommentRepository commentRepository,
                         ContentControlService contentControl,
                         ModerationLog moderationLog,
                         ContributionEvents contributionEvents) {
        this.appealRepository = appealRepository;
        this.recordRepository = recordRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.contentControl = contentControl;
        this.moderationLog = moderationLog;
        this.contributionEvents = contributionEvents;
    }

    @Transactional
    public AppealResponse appealPost(UUID postId, String statement, Viewer viewer) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new PostNotFoundException("Post bulunamadı: " + postId));
        requireAuthor(viewer, post.getAuthorId());
        ModerationAction action = POST_APPEALABLE.get(post.getStatus());
        return open(ModerationTarget.POST, post.getId(), post.getId(), action, statement, viewer, post.getTitle());
    }

    @Transactional
    public AppealResponse appealComment(UUID commentId, String statement, Viewer viewer) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("Yorum bulunamadı: " + commentId));
        requireAuthor(viewer, comment.getAuthorId());
        ModerationAction action = COMMENT_APPEALABLE.get(comment.getStatus());
        return open(ModerationTarget.COMMENT, comment.getId(), comment.getPostId(), action, statement, viewer, comment.getContent());
    }

    @Transactional(readOnly = true)
    public List<AppealResponse> myAppeals(Viewer viewer) {
        return appealRepository.findByAppellantIdOrderByCreatedAtDesc(viewer.id()).stream()
                .map(appeal -> AppealResponse.from(appeal, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AppealResponse> openAppeals(Viewer moderator, Pageable pageable) {
        moderator.requireModerator();
        return appealRepository.findByStatus(ModerationAppeal.Status.OPEN, pageable)
                .map(appeal -> AppealResponse.from(appeal, contentOf(appeal)));
    }

    @Transactional
    public AppealResponse decide(UUID appealId, AppealDecisionRequest request, Viewer moderator) {
        moderator.requireModerator();
        ModerationAppeal appeal = appealRepository.findById(appealId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "APPEAL_NOT_FOUND", "İtiraz bulunamadı."));
        if (appeal.getStatus() != ModerationAppeal.Status.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "APPEAL_CLOSED", "Bu itiraz zaten sonuçlandı.");
        }
        if (moderator.id().equals(appeal.getOriginalDeciderId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "SAME_DECIDER",
                    "İtirazı ilk kararı veren kişiden farklı bir moderatör veya admin değerlendirir.");
        }
        String reason = request.reason().strip();
        if (request.outcome() == AppealDecisionRequest.Outcome.ACCEPT) {
            reinstate(appeal, moderator, reason);
            appeal.decide(ModerationAppeal.Status.ACCEPTED, moderator.id(), reason, Instant.now());
            moderationLog.record(appeal.getTargetType(), appeal.getTargetId(), appeal.getPostId(), ModerationAction.APPEAL_ACCEPTED,
                    ModerationActor.MODERATOR, moderator.id(), reason);
        } else {
            appeal.decide(ModerationAppeal.Status.REJECTED, moderator.id(), reason, Instant.now());
            moderationLog.record(appeal.getTargetType(), appeal.getTargetId(), appeal.getPostId(), ModerationAction.APPEAL_REJECTED,
                    ModerationActor.MODERATOR, moderator.id(), reason);
        }
        appealRepository.save(appeal);
        log.info("Appeal decided. appealId={}, outcome={}, moderator={}", appealId, appeal.getStatus(), moderator.id());
        return AppealResponse.from(appeal, contentOf(appeal));
    }

    private AppealResponse open(ModerationTarget target, UUID targetId, UUID postId, ModerationAction action,
                                String statement, Viewer viewer, String content) {
        if (action == null) {
            throw notAppealable();
        }
        ModerationRecord decision = recordRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(target, targetId).stream()
                .filter(record -> record.getAction() == action)
                .findFirst()
                .orElseThrow(AppealService::notAppealable);
        if (appealRepository.existsByTargetTypeAndTargetId(target, targetId)) {
            throw new ApiException(HttpStatus.CONFLICT, "APPEAL_EXISTS", "Bu karara zaten itiraz ettiniz; itiraz hakkı bir kezdir.");
        }
        ModerationAppeal appeal = appealRepository.save(new ModerationAppeal(target, targetId, postId, viewer.id(),
                statement.strip(), action, decision.getActorId(), Instant.now()));
        log.info("Appeal opened. target={}, targetId={}, action={}", target, targetId, action);
        return AppealResponse.from(appeal, content);
    }

    private void reinstate(ModerationAppeal appeal, Viewer moderator, String reason) {
        if (appeal.getTargetType() == ModerationTarget.POST) {
            Post post = postRepository.findById(appeal.getTargetId()).orElseThrow(AppealService::contentChanged);
            if (POST_APPEALABLE.get(post.getStatus()) != appeal.getAppealedAction()) {
                throw contentChanged();
            }
            post.setStatus(PostStatus.PUBLISHED);
            post.setModerationFlag(null);
            post.setReviewNote(null);
            postRepository.save(post);
            contributionEvents.restore(post.getAuthorId(), post.getId());
        } else {
            Comment comment = commentRepository.findById(appeal.getTargetId()).orElseThrow(AppealService::contentChanged);
            if (COMMENT_APPEALABLE.get(comment.getStatus()) != appeal.getAppealedAction()) {
                throw contentChanged();
            }
            comment.setStatus(CommentStatus.PUBLISHED);
            comment.setModerationFlag(null);
            comment.setModerationNote(null);
            commentRepository.save(comment);
            contributionEvents.restore(comment.getAuthorId(), comment.getId());
        }
        contentControl.dismissReports(appeal.getTargetType(), appeal.getTargetId(), moderator.id(), reason);
    }

    private String contentOf(ModerationAppeal appeal) {
        if (appeal.getTargetType() == ModerationTarget.POST) {
            return postRepository.findById(appeal.getTargetId()).map(post -> post.getTitle() + "\n" + post.getContent()).orElse(null);
        }
        return commentRepository.findById(appeal.getTargetId()).map(Comment::getContent).orElse(null);
    }

    private static void requireAuthor(Viewer viewer, UUID authorId) {
        if (!viewer.id().equals(authorId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_CONTENT_AUTHOR", "Yalnız içeriğin yazarı itiraz edebilir.");
        }
    }

    private static ApiException notAppealable() {
        return new ApiException(HttpStatus.CONFLICT, "NOT_APPEALABLE",
                "İtiraz yalnız moderasyonla reddedilen, gizlenen veya kaldırılan içerik için yapılabilir.");
    }

    private static ApiException contentChanged() {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_CONTENT_STATE",
                "İçerik itiraz edildiğinden beri değişti; itirazı reddedip gerekçeyi yazın.");
    }
}
