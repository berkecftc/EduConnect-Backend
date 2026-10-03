package com.educonnect.postservice.service;

import com.educonnect.postservice.client.UserClient;
import com.educonnect.postservice.dto.ModerationQueueItem;
import com.educonnect.postservice.dto.ModerationRecordResponse;
import com.educonnect.postservice.dto.UserSummaryDto;
import com.educonnect.postservice.model.CommentStatus;
import com.educonnect.postservice.model.ModerationTarget;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.ModerationRecordRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ModerationQueueService {

    private static final Logger log = LoggerFactory.getLogger(ModerationQueueService.class);

    private static final List<PostStatus> OPEN_POSTS = List.of(PostStatus.IN_REVIEW);
    private static final List<CommentStatus> OPEN_COMMENTS = List.of(CommentStatus.IN_REVIEW);

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ModerationRecordRepository recordRepository;
    private final UserClient userClient;

    public ModerationQueueService(PostRepository postRepository,
                                  CommentRepository commentRepository,
                                  ModerationRecordRepository recordRepository,
                                  UserClient userClient) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.recordRepository = recordRepository;
        this.userClient = userClient;
    }

    @Transactional(readOnly = true)
    public Page<ModerationQueueItem> queue(ModerationTarget type, Viewer moderator, Pageable pageable) {
        moderator.requireModerator();
        Map<UUID, String> names = new HashMap<>();
        if (type == ModerationTarget.COMMENT) {
            return commentRepository.findByStatusIn(OPEN_COMMENTS, pageable).map(comment -> new ModerationQueueItem(
                    ModerationTarget.COMMENT, comment.getId(), comment.getPostId(), null, comment.getContent(),
                    comment.getAuthorId(), nameOf(comment.getAuthorId(), names), comment.getStatus().name(),
                    comment.getModerationFlag(), comment.getSubmittedAt()));
        }
        return postRepository.findByStatusIn(OPEN_POSTS, pageable).map(post -> new ModerationQueueItem(
                ModerationTarget.POST, post.getId(), post.getId(), post.getTitle(), post.getContent(),
                post.getAuthorId(), nameOf(post.getAuthorId(), names), post.getStatus().name(),
                post.getModerationFlag(), post.getSubmittedAt()));
    }

    @Transactional(readOnly = true)
    public List<ModerationRecordResponse> history(ModerationTarget type, UUID targetId, Viewer moderator) {
        moderator.requireModerator();
        return recordRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc(type, targetId).stream()
                .map(ModerationRecordResponse::from)
                .toList();
    }

    private String nameOf(UUID userId, Map<UUID, String> names) {
        if (userId == null) {
            return DeletedUser.DISPLAY_NAME;
        }
        if (names.containsKey(userId)) {
            return names.get(userId);
        }
        String name = null;
        try {
            UserSummaryDto user = userClient.getUserById(userId);
            if (user != null) {
                name = user.getFirstName() + " " + user.getLastName();
            }
        } catch (RuntimeException e) {
            log.warn("Author lookup failed for the moderation queue — userId: {}", userId);
        }
        names.put(userId, name);
        return name;
    }
}
