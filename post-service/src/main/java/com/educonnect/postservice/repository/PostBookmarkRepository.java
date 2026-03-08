package com.educonnect.postservice.repository;

import com.educonnect.postservice.model.PostBookmark;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostBookmarkRepository extends JpaRepository<PostBookmark, UUID> {

    /**
     * Kullanıcının belirli bir post'u kaydedip kaydetmediğini kontrol eder.
     */
    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    /**
     * Kullanıcının belirli bir post'a ait kaydını bulur (toggle için).
     */
    Optional<PostBookmark> findByPostIdAndUserId(UUID postId, UUID userId);

    /**
     * Kullanıcının kaydettiği post'ları sayfalayarak döndürür.
     */
    Page<PostBookmark> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}

