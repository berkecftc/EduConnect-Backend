package com.educonnect.postservice.service;

import com.educonnect.postservice.dto.LikeResponse;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostLike;
import com.educonnect.postservice.repository.PostLikeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Beğeni iş mantığı katmanı.
 * Toggle (beğen/beğeniyi geri al) mantığı ile çalışır.
 */
@Service
public class PostLikeService {

    private static final Logger log = LoggerFactory.getLogger(PostLikeService.class);

    private final PostLikeRepository postLikeRepository;
    private final PostVisibility postVisibility;
    private final ContributionEvents contributionEvents;

    public PostLikeService(PostLikeRepository postLikeRepository, PostVisibility postVisibility,
                           ContributionEvents contributionEvents) {
        this.postLikeRepository = postLikeRepository;
        this.postVisibility = postVisibility;
        this.contributionEvents = contributionEvents;
    }

    /**
     * Toggle like: Beğenmişse geri al, beğenmemişse beğen.
     */
    @Transactional
    public LikeResponse toggleLike(UUID postId, Viewer viewer) {
        validateLikeablePost(postId, viewer);
        UUID userId = viewer.id();

        Optional<PostLike> existingLike = postLikeRepository.findByPostIdAndUserId(postId, userId);

        if (existingLike.isPresent()) {
            return unlikePost(postId, viewer);
        }

        return likePost(postId, viewer);
    }

    /**
     * Post'u beğenir. Kullanıcı zaten beğenmişse idempotent şekilde mevcut durumu döner.
     */
    @Transactional
    public LikeResponse likePost(UUID postId, Viewer viewer) {
        Post post = validateLikeablePost(postId, viewer);
        UUID userId = viewer.id();

        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            long count = postLikeRepository.countByPostId(postId);
            return new LikeResponse(true, count);
        }

        PostLike like = new PostLike();
        like.setPostId(postId);
        like.setUserId(userId);
        postLikeRepository.save(like);
        contributionEvents.noteLiked(post, userId);
        log.info("Post beğenildi — postId: {}, userId: {}", postId, userId);

        long count = postLikeRepository.countByPostId(postId);
        return new LikeResponse(true, count);
    }

    /**
     * Post beğenisini kaldırır. Kullanıcı daha önce beğenmemişse idempotent şekilde mevcut durumu döner.
     */
    @Transactional
    public LikeResponse unlikePost(UUID postId, Viewer viewer) {
        validateLikeablePost(postId, viewer);
        UUID userId = viewer.id();

        Optional<PostLike> existingLike = postLikeRepository.findByPostIdAndUserId(postId, userId);

        if (existingLike.isPresent()) {
            postLikeRepository.delete(existingLike.get());
            log.info("Beğeni geri alındı — postId: {}, userId: {}", postId, userId);
        }

        long count = postLikeRepository.countByPostId(postId);
        return new LikeResponse(false, count);
    }

    /**
     * Bir post'un toplam beğeni sayısını döndürür.
     */
    public long getLikeCount(UUID postId) {
        return postLikeRepository.countByPostId(postId);
    }

    /**
     * Kullanıcının bir post'u beğenip beğenmediğini kontrol eder.
     */
    public boolean isLikedByUser(UUID postId, UUID userId) {
        return postLikeRepository.existsByPostIdAndUserId(postId, userId);
    }

    private Post validateLikeablePost(UUID postId, Viewer viewer) {
        return postVisibility.requirePublished(postId, viewer, "Sadece yayınlanmış postlar beğenilebilir.");
    }
}
