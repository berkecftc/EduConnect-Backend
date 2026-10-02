package com.educonnect.postservice.repository;

import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.CommentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    Page<Comment> findByPostIdAndStatusAndParentCommentIdIsNull(UUID postId, CommentStatus status, Pageable pageable);

    List<Comment> findByParentCommentIdAndStatus(UUID parentCommentId, CommentStatus status);

    long countByPostIdAndStatus(UUID postId, CommentStatus status);

    @Query("select c from Comment c where c.postId = :postId and c.parentCommentId is null "
            + "and (c.status = :published or c.authorId = :viewerId)")
    Page<Comment> findVisibleTopLevel(@Param("postId") UUID postId, @Param("viewerId") UUID viewerId,
                                      @Param("published") CommentStatus published, Pageable pageable);

    @Query("select c from Comment c where c.parentCommentId = :parentId "
            + "and (c.status = :published or c.authorId = :viewerId) order by c.createdAt asc")
    List<Comment> findVisibleReplies(@Param("parentId") UUID parentId, @Param("viewerId") UUID viewerId,
                                     @Param("published") CommentStatus published);

    List<Comment> findByStatusAndSubmittedAtBefore(CommentStatus status, Instant cutoff);

    Page<Comment> findByStatusIn(Collection<CommentStatus> statuses, Pageable pageable);
}
