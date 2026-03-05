package com.educonnect.postservice.repository;

import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    /**
     * Belirli statüdeki postları sayfalayarak döndürür.
     * Sadece PUBLISHED olanları listelemek için kullanılır.
     * Tek sorgu çalışır — N+1 riski yoktur.
     */
    Page<Post> findByStatus(PostStatus status, Pageable pageable);

    /**
     * Verilen post'un belirtilen yazara ait olup olmadığını kontrol eder.
     * Service katmanında yetki doğrulaması için kullanılır.
     */
    boolean existsByIdAndAuthorId(UUID id, UUID authorId);
}

