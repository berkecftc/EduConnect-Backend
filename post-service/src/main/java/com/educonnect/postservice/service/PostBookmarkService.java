package com.educonnect.postservice.service;

import com.educonnect.postservice.dto.BookmarkResponse;
import com.educonnect.postservice.model.PostBookmark;
import com.educonnect.postservice.repository.PostBookmarkRepository;
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
    private final PostVisibility postVisibility;

    public PostBookmarkService(PostBookmarkRepository postBookmarkRepository, PostVisibility postVisibility) {
        this.postBookmarkRepository = postBookmarkRepository;
        this.postVisibility = postVisibility;
    }

    /**
     * Toggle bookmark: Kaydedilmişse geri al, kaydedilmemişse kaydet.
     */
    @Transactional
    public BookmarkResponse toggleBookmark(UUID postId, Viewer viewer) {
        postVisibility.requireVisible(postId, viewer);
        UUID userId = viewer.id();

        Optional<PostBookmark> existingBookmark = postBookmarkRepository.findByPostIdAndUserId(postId, userId);

        if (existingBookmark.isPresent()) {
            // Kaydı geri al
            postBookmarkRepository.delete(existingBookmark.get());
            log.info("Kayıt geri alındı — postId: {}, userId: {}", postId, userId);
            return new BookmarkResponse(false);
        } else {
            // Kaydet
            PostBookmark bookmark = new PostBookmark();
            bookmark.setPostId(postId);
            bookmark.setUserId(userId);
            postBookmarkRepository.save(bookmark);
            log.info("Post kaydedildi — postId: {}, userId: {}", postId, userId);
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
