package com.educonnect.postservice.service;

import com.educonnect.postservice.dto.LikeResponse;
import com.educonnect.postservice.exception.PostNotFoundException;
import com.educonnect.postservice.model.PostLike;
import com.educonnect.postservice.repository.PostLikeRepository;
import com.educonnect.postservice.repository.PostRepository;
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
    private final PostRepository postRepository;

    public PostLikeService(PostLikeRepository postLikeRepository, PostRepository postRepository) {
        this.postLikeRepository = postLikeRepository;
        this.postRepository = postRepository;
    }

    /**
     * Toggle like: Beğenmişse geri al, beğenmemişse beğen.
     */
    @Transactional
    public LikeResponse toggleLike(UUID postId, UUID userId) {
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException("Post bulunamadı: " + postId);
        }

        Optional<PostLike> existingLike = postLikeRepository.findByPostIdAndUserId(postId, userId);

        if (existingLike.isPresent()) {
            // Beğeni geri al
            postLikeRepository.delete(existingLike.get());
            log.info("👎 Beğeni geri alındı — postId: {}, userId: {}", postId, userId);
            long count = postLikeRepository.countByPostId(postId);
            return new LikeResponse(false, count);
        } else {
            // Beğen
            PostLike like = new PostLike();
            like.setPostId(postId);
            like.setUserId(userId);
            postLikeRepository.save(like);
            log.info("👍 Post beğenildi — postId: {}, userId: {}", postId, userId);
            long count = postLikeRepository.countByPostId(postId);
            return new LikeResponse(true, count);
        }
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
}

