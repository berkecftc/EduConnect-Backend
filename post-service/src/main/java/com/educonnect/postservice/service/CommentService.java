package com.educonnect.postservice.service;

import com.educonnect.postservice.client.UserClient;
import com.educonnect.postservice.dto.CommentResponse;
import com.educonnect.postservice.dto.CreateCommentRequest;
import com.educonnect.postservice.dto.UserSummaryDto;
import com.educonnect.postservice.event.PostModerationEvent;
import com.educonnect.postservice.exception.CommentNotFoundException;
import com.educonnect.postservice.exception.UnauthorizedPostAccessException;
import com.educonnect.postservice.messaging.PostEventPublisher;
import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.CommentStatus;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserClient userClient;
    private final PostVisibility postVisibility;
    private final PostEventPublisher eventPublisher;
    private final ContributionEvents contributionEvents;

    public CommentService(CommentRepository commentRepository,
                          PostRepository postRepository,
                          UserClient userClient,
                          PostVisibility postVisibility,
                          PostEventPublisher eventPublisher,
                          ContributionEvents contributionEvents) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userClient = userClient;
        this.postVisibility = postVisibility;
        this.eventPublisher = eventPublisher;
        this.contributionEvents = contributionEvents;
    }

    @Transactional
    public CommentResponse createComment(UUID postId, CreateCommentRequest request, Viewer viewer) {
        postVisibility.requireOpenForComments(postId, viewer);
        if (request.parentCommentId() != null) {
            Comment parentComment = commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new CommentNotFoundException(
                            "Yanıt verilecek yorum bulunamadı: " + request.parentCommentId()));
            if (!parentComment.getPostId().equals(postId)) {
                throw new IllegalArgumentException("Yanıt verilecek yorum bu post'a ait değil.");
            }
            requireRepliable(parentComment);
        }
        return submit(postId, request.parentCommentId(), request.content(), viewer);
    }

    @Transactional
    public CommentResponse createReply(UUID parentCommentId, String content, Viewer viewer) {
        Comment parentComment = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new CommentNotFoundException("Yanıt verilecek yorum bulunamadı: " + parentCommentId));
        postVisibility.requireOpenForComments(parentComment.getPostId(), viewer);
        requireRepliable(parentComment);
        return submit(parentComment.getPostId(), parentCommentId, content, viewer);
    }

    @Transactional
    public void deleteComment(UUID postId, UUID commentId, UUID authorId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("Yorum bulunamadı: " + commentId));

        if (!comment.getPostId().equals(postId)) {
            throw new IllegalArgumentException("Yorum bu post'a ait değil.");
        }

        if (!authorId.equals(comment.getAuthorId())) {
            throw new UnauthorizedPostAccessException(
                    "Bu yorumu sadece yazarı silebilir. commentId: " + commentId);
        }

        contributionEvents.revoke(comment.getAuthorId(), comment.getId());
        commentRepository.delete(comment);
        log.info("Yorum silindi — commentId: {}, postId: {}, authorId: {}", commentId, postId, authorId);
    }

    @Transactional(readOnly = true)
    public Page<CommentResponse> getCommentsByPostId(UUID postId, Viewer viewer, Pageable pageable) {
        postVisibility.requireVisible(postId, viewer);

        Page<Comment> topLevelComments = commentRepository.findVisibleTopLevel(postId, viewer.id(),
                CommentStatus.PUBLISHED, pageable);

        Map<UUID, List<Comment>> repliesMap = new HashMap<>();
        List<UUID> allAuthorIds = new ArrayList<>();
        for (Comment comment : topLevelComments.getContent()) {
            allAuthorIds.add(comment.getAuthorId());
            List<Comment> replies = comment.getStatus() == CommentStatus.PUBLISHED
                    ? commentRepository.findVisibleReplies(comment.getId(), viewer.id(), CommentStatus.PUBLISHED)
                    : List.of();
            repliesMap.put(comment.getId(), replies);
            replies.forEach(reply -> allAuthorIds.add(reply.getAuthorId()));
        }

        Map<UUID, UserSummaryDto> userCache = fetchUsersSafely(allAuthorIds);

        return topLevelComments.map(comment -> {
            List<CommentResponse> replyResponses = repliesMap.getOrDefault(comment.getId(), Collections.emptyList()).stream()
                    .map(reply -> mapToResponse(reply, userCache.get(reply.getAuthorId()), Collections.emptyList()))
                    .toList();
            return mapToResponse(comment, userCache.get(comment.getAuthorId()), replyResponses);
        });
    }

    public long getPublishedCommentCount(UUID postId) {
        return commentRepository.countByPostIdAndStatus(postId, CommentStatus.PUBLISHED);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getRepliesByCommentId(UUID commentId, Viewer viewer) {
        Comment parentComment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("Yorum bulunamadı: " + commentId));
        boolean visible = postRepository.findById(parentComment.getPostId())
                .map(post -> postVisibility.canSee(post, viewer))
                .orElse(false)
                && (parentComment.getStatus() == CommentStatus.PUBLISHED || viewer.id().equals(parentComment.getAuthorId()));
        if (!visible) {
            throw new CommentNotFoundException("Yorum bulunamadı: " + commentId);
        }

        List<Comment> replies = commentRepository.findVisibleReplies(commentId, viewer.id(), CommentStatus.PUBLISHED);
        Map<UUID, UserSummaryDto> userCache = fetchUsersSafely(replies.stream().map(Comment::getAuthorId).toList());

        return replies.stream()
                .map(reply -> mapToResponse(reply, userCache.get(reply.getAuthorId()), Collections.emptyList()))
                .toList();
    }

    private CommentResponse submit(UUID postId, UUID parentCommentId, String content, Viewer viewer) {
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(viewer.id());
        comment.setParentCommentId(parentCommentId);
        comment.setContent(content);
        comment.setStatus(CommentStatus.PENDING);
        comment.setSubmittedAt(Instant.now());

        Comment saved = commentRepository.save(comment);
        eventPublisher.publishModerationEvent(PostModerationEvent.forComment(postId, saved.getId(), content));
        log.info("Yorum moderasyona gönderildi — commentId: {}, postId: {}, authorId: {}", saved.getId(), postId, viewer.id());

        return mapToResponse(saved, fetchUserSafely(viewer.id()), Collections.emptyList());
    }

    private static void requireRepliable(Comment parentComment) {
        if (parentComment.getParentCommentId() != null) {
            throw new IllegalArgumentException(
                    "Yanıta yanıt verilemez. Sadece üst seviye yorumlara yanıt verilebilir.");
        }
        if (parentComment.getStatus() != CommentStatus.PUBLISHED) {
            throw new IllegalArgumentException("Sadece yayınlanmış yorumlara yanıt verilebilir.");
        }
    }

    private CommentResponse mapToResponse(Comment comment, UserSummaryDto user, List<CommentResponse> replies) {
        String authorName = null;
        if (comment.getAuthorId() == null) {
            authorName = DeletedUser.DISPLAY_NAME;
        } else if (user != null) {
            authorName = user.getFirstName() + " " + user.getLastName();
        }

        return new CommentResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getAuthorId(),
                authorName,
                comment.getParentCommentId(),
                comment.getContent(),
                comment.getStatus(),
                comment.getModerationNote(),
                replies,
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }

    private Map<UUID, UserSummaryDto> fetchUsersSafely(Collection<UUID> userIds) {
        Map<UUID, UserSummaryDto> users = new HashMap<>();
        for (UUID userId : new LinkedHashSet<>(userIds)) {
            UserSummaryDto user = fetchUserSafely(userId);
            if (user != null) {
                users.put(userId, user);
            }
        }
        return users;
    }

    private UserSummaryDto fetchUserSafely(UUID userId) {
        if (userId == null) {
            return null;
        }
        try {
            return userClient.getUserById(userId);
        } catch (Exception e) {
            log.warn("Kullanıcı bilgisi alınamadı — userId: {}", userId);
            return null;
        }
    }
}
