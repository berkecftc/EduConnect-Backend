package com.educonnect.postservice.service;

import com.educonnect.common.web.ApiException;
import com.educonnect.postservice.client.UserClient;
import com.educonnect.postservice.dto.CreatePostRequest;
import com.educonnect.postservice.dto.PostFeedFilter;
import com.educonnect.postservice.dto.PostResponse;
import com.educonnect.postservice.dto.RecentPostDto;
import com.educonnect.postservice.dto.UpdatePostRequest;
import com.educonnect.postservice.dto.UserSummaryDto;
import com.educonnect.postservice.event.PostModerationEvent;
import com.educonnect.postservice.exception.PostNotFoundException;
import com.educonnect.postservice.exception.UnauthorizedPostAccessException;
import com.educonnect.postservice.messaging.PostEventPublisher;
import com.educonnect.postservice.model.CommentStatus;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostBookmark;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.model.PublisherType;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.PostBookmarkRepository;
import com.educonnect.postservice.repository.PostLikeRepository;
import com.educonnect.postservice.repository.PostRepository;
import com.educonnect.postservice.repository.PostSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostRepository postRepository;
    private final PostEventPublisher eventPublisher;
    private final UserClient userClient;
    private final PostLikeRepository postLikeRepository;
    private final PostBookmarkRepository postBookmarkRepository;
    private final CommentRepository commentRepository;
    private final PublisherPolicy publisherPolicy;
    private final PostVisibility postVisibility;
    private final ScopeAccessService scopeAccess;
    private final AttachmentService attachmentService;
    private final ContributionEvents contributionEvents;

    public PostService(PostRepository postRepository,
                       PostEventPublisher eventPublisher,
                       UserClient userClient,
                       PostLikeRepository postLikeRepository,
                       PostBookmarkRepository postBookmarkRepository,
                       CommentRepository commentRepository,
                       PublisherPolicy publisherPolicy,
                       PostVisibility postVisibility,
                       ScopeAccessService scopeAccess,
                       AttachmentService attachmentService,
                       ContributionEvents contributionEvents) {
        this.postRepository = postRepository;
        this.eventPublisher = eventPublisher;
        this.userClient = userClient;
        this.postLikeRepository = postLikeRepository;
        this.postBookmarkRepository = postBookmarkRepository;
        this.commentRepository = commentRepository;
        this.publisherPolicy = publisherPolicy;
        this.postVisibility = postVisibility;
        this.scopeAccess = scopeAccess;
        this.attachmentService = attachmentService;
        this.contributionEvents = contributionEvents;
    }

    @Transactional
    public PostResponse createPost(CreatePostRequest request, Viewer viewer) {
        PostCategory category = request.categoryOrDefault();
        Publication publication = publisherPolicy.resolve(viewer, category, request.publisherType(),
                request.clubId(), request.courseId(), request.publisherName());

        Post post = new Post();
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setCategory(category);
        post.setAuthorId(viewer.id());
        post.setPublisherType(publication.type());
        post.setClubId(publication.clubId());
        post.setCourseId(publication.courseId());
        post.setPublisherName(publication.name());
        post.setCourseLabel(publication.courseLabel());
        if (category == PostCategory.DERS_NOTU) {
            requireDeclaration(request.sharingDeclaration());
            post.setDeclarationAcceptedAt(Instant.now());
        }
        post.setCommentsDisabled(category.official() && Boolean.TRUE.equals(request.commentsDisabled()));
        post.setStatus(publication.needsApproval() ? PostStatus.AWAITING_APPROVAL : PostStatus.PENDING);

        Post savedPost = postRepository.save(post);
        log.info("Post oluşturuldu ({}) — postId: {}, authorId: {}", savedPost.getStatus(), savedPost.getId(), viewer.id());
        submitForModeration(savedPost);

        return mapToResponseWithUser(savedPost, fetchUserSafely(viewer.id()), viewer.id());
    }

    @Transactional
    public PostResponse updatePost(UUID postId, UpdatePostRequest request, Viewer viewer) {
        Post post = findPostOrThrow(postId);
        validateAuthor(post, viewer.id());
        if (post.getStatus() == PostStatus.HIDDEN || post.getStatus() == PostStatus.REMOVED) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTENT_LOCKED",
                    "Gizlenen veya kaldırılan gönderi düzenlenemez; karara itiraz edebilirsiniz.");
        }

        PostCategory category = request.category() != null ? request.category() : post.getCategory();
        if (category.official() != post.isOfficial()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_CHANGE_NOT_ALLOWED",
                    "Duyuru forum gönderisine, forum gönderisi duyuruya çevrilemez.");
        }
        boolean needsApproval = false;
        if (post.isOfficial()) {
            needsApproval = publisherPolicy.reauthorize(viewer, post).needsApproval();
            if (request.commentsDisabled() != null) {
                post.setCommentsDisabled(request.commentsDisabled());
            }
        } else {
            publisherPolicy.requireForumWriter(viewer);
            if (post.getCourseId() != null && category != PostCategory.SORU && category != PostCategory.DERS_NOTU) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SCOPE_NOT_ALLOWED",
                        "Derse bağlı gönderi yalnız soru veya ders notu olabilir.");
            }
            if (category == PostCategory.DERS_NOTU && post.getDeclarationAcceptedAt() == null) {
                requireDeclaration(request.sharingDeclaration());
                post.setDeclarationAcceptedAt(Instant.now());
            }
        }

        post.setTitle(request.title());
        post.setContent(request.content());
        post.setCategory(category);
        post.setReviewNote(null);
        post.setStatus(needsApproval ? PostStatus.AWAITING_APPROVAL : PostStatus.PENDING);

        Post updatedPost = postRepository.save(post);
        log.info("Post güncellendi ({}) — postId: {}, authorId: {}", updatedPost.getStatus(), updatedPost.getId(), viewer.id());
        submitForModeration(updatedPost);

        return mapToResponseWithUser(updatedPost, fetchUserSafely(viewer.id()), viewer.id());
    }

    @Transactional
    public void deletePost(UUID postId, UUID authorId) {
        Post post = findPostOrThrow(postId);
        validateAuthor(post, authorId);

        attachmentService.discard(post);
        contributionEvents.revoke(post.getAuthorId(), post.getId());
        postRepository.delete(post);
        log.info("Post silindi — postId: {}, authorId: {}", postId, authorId);
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getPublishedPosts(PostFeedFilter filter, Pageable pageable, Viewer viewer) {
        boolean allCourses = viewer.seesAllScopes();
        Set<UUID> courseIds = allCourses || !mayIncludeCourseAnnouncements(filter)
                ? Set.of()
                : scopeAccess.memberCourseIds(viewer);
        Page<Post> postPage = postRepository.findAll(
                PostSpecifications.publishedFeed(filter, allCourses, courseIds), pageable);
        return mapPage(postPage, viewer.id());
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getSavedPosts(Viewer viewer, Pageable pageable) {
        Page<PostBookmark> bookmarkPage = postBookmarkRepository.findByUserIdOrderByCreatedAtDesc(viewer.id(), pageable);

        List<UUID> postIds = bookmarkPage.getContent().stream()
                .map(PostBookmark::getPostId)
                .toList();

        Map<UUID, Post> postMap = postRepository.findAllById(postIds).stream()
                .filter(post -> post.getStatus() == PostStatus.PUBLISHED && postVisibility.canSee(post, viewer))
                .collect(Collectors.toMap(Post::getId, Function.identity()));

        Map<UUID, UserSummaryDto> userCache = fetchUsersSafely(postMap.values().stream()
                .map(Post::getAuthorId)
                .distinct()
                .toList());

        List<PostResponse> responses = bookmarkPage.getContent().stream()
                .filter(bookmark -> postMap.containsKey(bookmark.getPostId()))
                .map(bookmark -> {
                    Post post = postMap.get(bookmark.getPostId());
                    return mapToResponseWithUser(post, userCache.get(post.getAuthorId()), viewer.id());
                })
                .toList();

        return new PageImpl<>(responses, pageable, bookmarkPage.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getMyPosts(UUID authorId, Pageable pageable) {
        Page<Post> postPage = postRepository.findByAuthorId(authorId, pageable);
        UserSummaryDto author = postPage.isEmpty() ? null : fetchUserSafely(authorId);
        return postPage.map(post -> mapToResponseWithUser(post, author, authorId));
    }

    @Transactional(readOnly = true)
    public PostResponse getPostById(UUID postId, Viewer viewer) {
        Post post = postVisibility.requireVisible(postId, viewer);
        return mapToResponseWithUser(post, fetchUserSafely(post.getAuthorId()), viewer.id());
    }

    @Transactional(readOnly = true)
    public Page<PostResponse> getAwaitingClubApproval(UUID clubId, Viewer viewer, Pageable pageable) {
        publisherPolicy.requireClubApprover(viewer, clubId);
        return mapPage(postRepository.findByClubIdAndStatus(clubId, PostStatus.AWAITING_APPROVAL, pageable), viewer.id());
    }

    @Transactional
    public PostResponse approveClubAnnouncement(UUID postId, Viewer viewer) {
        Post post = findAwaitingApproval(postId, viewer);
        post.approve(viewer.id(), Instant.now());
        post.setStatus(PostStatus.PENDING);
        Post saved = postRepository.save(post);
        log.info("Kulüp duyurusu başkan tarafından onaylandı — postId: {}, approver: {}", postId, viewer.id());
        submitForModeration(saved);
        return mapToResponseWithUser(saved, fetchUserSafely(saved.getAuthorId()), viewer.id());
    }

    @Transactional
    public PostResponse rejectClubAnnouncement(UUID postId, String note, Viewer viewer) {
        Post post = findAwaitingApproval(postId, viewer);
        post.setStatus(PostStatus.REJECTED);
        post.setReviewNote(note.strip());
        Post saved = postRepository.save(post);
        log.info("Kulüp duyurusu başkan tarafından reddedildi — postId: {}, reviewer: {}", postId, viewer.id());
        return mapToResponseWithUser(saved, fetchUserSafely(saved.getAuthorId()), viewer.id());
    }

    @Transactional(readOnly = true)
    public List<RecentPostDto> getRecentPostsByUser(UUID userId) {
        return postRepository.findTop5ByAuthorIdAndStatusOrderByCreatedAtDesc(userId, PostStatus.PUBLISHED)
                .stream()
                .filter(post -> post.getPublisherType() != PublisherType.COURSE)
                .map(post -> new RecentPostDto(
                        post.getId(),
                        post.getTitle(),
                        post.getContent(),
                        post.getCreatedAt()
                ))
                .toList();
    }

    private Post findAwaitingApproval(UUID postId, Viewer viewer) {
        Post post = findPostOrThrow(postId);
        if (post.getPublisherType() != PublisherType.CLUB) {
            throw new PostNotFoundException("Post bulunamadı: " + postId);
        }
        publisherPolicy.requireClubApprover(viewer, post.getClubId());
        if (post.getStatus() != PostStatus.AWAITING_APPROVAL) {
            throw new ApiException(HttpStatus.CONFLICT, "NOT_AWAITING_APPROVAL", "Bu duyuru onay beklemiyor.");
        }
        return post;
    }

    private static void requireDeclaration(Boolean declared) {
        if (!Boolean.TRUE.equals(declared)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DECLARATION_REQUIRED",
                    "Ders notunu paylaşmadan önce kendi notunuz olduğunu, sınav sorusu veya telifli materyal içermediğini onaylayın.");
        }
    }

    private static boolean mayIncludeCourseAnnouncements(PostFeedFilter filter) {
        if (filter.publisherType() != null && filter.publisherType() != PublisherType.COURSE) {
            return false;
        }
        if (Boolean.FALSE.equals(filter.official())) {
            return false;
        }
        return filter.category() == null || filter.category().official();
    }

    private Page<PostResponse> mapPage(Page<Post> postPage, UUID currentUserId) {
        Map<UUID, UserSummaryDto> userCache = fetchUsersSafely(postPage.getContent().stream()
                .map(Post::getAuthorId)
                .distinct()
                .toList());
        return postPage.map(post -> mapToResponseWithUser(post, userCache.get(post.getAuthorId()), currentUserId));
    }

    private Post findPostOrThrow(UUID postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post bulunamadı: " + postId));
    }

    private void validateAuthor(Post post, UUID authorId) {
        if (!authorId.equals(post.getAuthorId())) {
            throw new UnauthorizedPostAccessException(
                    "Bu işlemi sadece post'un yazarı yapabilir. postId: " + post.getId()
            );
        }
    }

    private void submitForModeration(Post post) {
        if (post.getStatus() != PostStatus.PENDING) {
            return;
        }
        post.setSubmittedAt(Instant.now());
        post.setModerationFlag(null);
        eventPublisher.publishModerationEvent(new PostModerationEvent(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                UUID.randomUUID()
        ));
    }

    private PostResponse mapToResponseWithUser(Post post, UserSummaryDto user, UUID currentUserId) {
        String authorName = null;
        String authorDepartment = null;

        if (post.getAuthorId() == null) {
            authorName = DeletedUser.DISPLAY_NAME;
        } else if (user != null) {
            authorName = user.getFirstName() + " " + user.getLastName();
            authorDepartment = user.getDepartment();
        }

        long likeCount = postLikeRepository.countByPostId(post.getId());
        long commentCount = commentRepository.countByPostIdAndStatus(post.getId(), CommentStatus.PUBLISHED);
        boolean liked = currentUserId != null && postLikeRepository.existsByPostIdAndUserId(post.getId(), currentUserId);
        boolean bookmarked = currentUserId != null && postBookmarkRepository.existsByPostIdAndUserId(post.getId(), currentUserId);

        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getCategory(),
                post.getStatus(),
                post.getPublisherType(),
                post.getClubId(),
                post.getCourseId(),
                post.getPublisherName(),
                post.isOfficial(),
                post.isCommentsDisabled(),
                post.getReviewNote(),
                post.getCourseLabel(),
                post.getAttachmentName(),
                post.getAcceptedCommentId(),
                post.getAuthorId(),
                authorName,
                authorDepartment,
                likeCount,
                commentCount,
                liked,
                bookmarked,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    private Map<UUID, UserSummaryDto> fetchUsersSafely(List<UUID> userIds) {
        Map<UUID, UserSummaryDto> users = new HashMap<>();
        for (UUID userId : userIds) {
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
            log.warn("Kullanıcı bilgisi alınamadı (user-service erişilemez olabilir) — userId: {}", userId);
            return null;
        }
    }
}
