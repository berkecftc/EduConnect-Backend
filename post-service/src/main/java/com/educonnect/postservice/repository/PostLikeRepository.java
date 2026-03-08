package com.educonnect.postservice.repository;

import com.educonnect.postservice.model.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    /**
     * Kullanıcının belirli bir post'u beğenip beğenmediğini kontrol eder.
     */
    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    /**
     * Kullanıcının belirli bir post'a ait beğenisini bulur (toggle için).
     */
    Optional<PostLike> findByPostIdAndUserId(UUID postId, UUID userId);

    /**
     * Bir post'un toplam beğeni sayısını döndürür.
     */
    long countByPostId(UUID postId);
}

