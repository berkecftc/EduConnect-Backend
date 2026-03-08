package com.educonnect.postservice.repository;

import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    /**
     * Bir post'a ait üst seviye yorumları (parent_comment_id IS NULL) sayfalayarak döndürür.
     * Sadece belirli statüdeki yorumlar listelenir.
     */
    Page<Comment> findByPostIdAndStatusAndParentCommentIdIsNull(UUID postId, CommentStatus status, Pageable pageable);

    /**
     * Bir üst yoruma ait yanıtları döndürür.
     * Sadece belirli statüdeki yanıtlar listelenir.
     */
    List<Comment> findByParentCommentIdAndStatus(UUID parentCommentId, CommentStatus status);

    /**
     * Bir post'a ait yayınlanmış yorum sayısını döndürür (üst + yanıt dahil).
     */
    long countByPostIdAndStatus(UUID postId, CommentStatus status);
}

