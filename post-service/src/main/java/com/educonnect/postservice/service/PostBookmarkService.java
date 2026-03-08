package com.educonnect.postservice.service;

import com.educonnect.postservice.dto.BookmarkResponse;
import com.educonnect.postservice.exception.PostNotFoundException;
import com.educonnect.postservice.model.PostBookmark;
import com.educonnect.postservice.repository.PostBookmarkRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Kaydetme (bookmark) iş mantığı katmanı.
 * Toggle (kaydet/kaydı geri al) mantığı ile çalışır.
 */
@Service
public class PostBookmarkService {

    private static final Logger log = LoggerFactory.getLogger(PostBookmarkService.class);

    private final PostBookmarkRepository postBookmarkRepository;
    private final PostRepository postRepository;

    public PostBookmarkService(PostBookmarkRepository postBookmarkRepository, PostRepository postRepository) {
        this.postBookmarkRepository = postBookmarkRepository;
        this.postRepository = postRepository;
    }

    /**
     * Toggle bookmark: Kaydedilmişse geri al, kaydedilmemişse kaydet.
     */
    @Transactional
    public BookmarkResponse toggleBookmark(UUID postId, UUID userId) {
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException("Post bulunamadı: " + postId);
        }

        Optional<PostBookmark> existingBookmark = postBookmarkRepository.findByPostIdAndUserId(postId, userId);

        if (existingBookmark.isPresent()) {
            // Kaydı geri al
            postBookmarkRepository.delete(existingBookmark.get());
            log.info("🔖 Kayıt geri alındı — postId: {}, userId: {}", postId, userId);
            return new BookmarkResponse(false);
        } else {
            // Kaydet
            PostBookmark bookmark = new PostBookmark();
            bookmark.setPostId(postId);
            bookmark.setUserId(userId);
            postBookmarkRepository.save(bookmark);
            log.info("📌 Post kaydedildi — postId: {}, userId: {}", postId, userId);
            return new BookmarkResponse(true);
        }
    }

    /**
     * Kullanıcının bir post'u kaydedip kaydetmediğini kontrol eder.
     */
    public boolean isBookmarkedByUser(UUID postId, UUID userId) {
        return postBookmarkRepository.existsByPostIdAndUserId(postId, userId);
    }
}

