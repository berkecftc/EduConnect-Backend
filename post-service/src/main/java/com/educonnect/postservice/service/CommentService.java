package com.educonnect.postservice.service;

import com.educonnect.postservice.client.UserClient;
import com.educonnect.postservice.dto.CommentResponse;
import com.educonnect.postservice.dto.CreateCommentRequest;
import com.educonnect.postservice.dto.UserSummaryDto;
import com.educonnect.postservice.exception.CommentNotFoundException;
import com.educonnect.postservice.exception.PostNotFoundException;
import com.educonnect.postservice.exception.UnauthorizedPostAccessException;
import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.CommentStatus;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.PostRepository;
import com.educonnect.postservice.util.BlacklistProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Yorum iş mantığı katmanı.
 *
 * Özellikler:
 * - Yorum oluşturma (blacklist kontrolü ile — kötü kelime varsa REJECTED, yoksa PUBLISHED).
 * - Üst yoruma yanıt verme (tek seviye derinlik — yanıta yanıt verilmez).
 * - Yorum silme (sadece yazar silebilir).
 * - Post'a ait yorumları listeleme (üst yorumlar + iç içe replies).
 */
@Service
public class CommentService {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final BlacklistProvider blacklistProvider;
    private final UserClient userClient;

    public CommentService(CommentRepository commentRepository,
                          PostRepository postRepository,
                          BlacklistProvider blacklistProvider,
                          UserClient userClient) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.blacklistProvider = blacklistProvider;
        this.userClient = userClient;
    }

    /**
     * Yeni yorum oluşturur.
     * - Post'un PUBLISHED statüsünde olması gerekir.
     * - parentCommentId verilmişse, üst yorum aynı post'a ait olmalı ve kendisi üst seviye olmalı.
     * - İçerik blacklist kontrolünden geçer: kötü kelime → REJECTED, temiz → PUBLISHED.
     */
    @Transactional
    public CommentResponse createComment(UUID postId, CreateCommentRequest request, UUID authorId) {
        // Post var mı ve yayınlanmış mı kontrol et
        var post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post bulunamadı: " + postId));

        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new IllegalArgumentException("Sadece yayınlanmış postlara yorum yapılabilir.");
        }

        // Yanıt kontrolü: parentCommentId varsa validasyon yap
        if (request.parentCommentId() != null) {
            Comment parentComment = commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new CommentNotFoundException(
                            "Yanıt verilecek yorum bulunamadı: " + request.parentCommentId()));

            if (!parentComment.getPostId().equals(postId)) {
                throw new IllegalArgumentException("Yanıt verilecek yorum bu post'a ait değil.");
            }

            // Tek seviye derinlik — yanıta yanıt verilmez
            if (parentComment.getParentCommentId() != null) {
                throw new IllegalArgumentException(
                        "Yanıta yanıt verilemez. Sadece üst seviye yorumlara yanıt verilebilir.");
            }
        }

        // Blacklist kontrolü — senkron moderasyon
        boolean containsBadWord = blacklistProvider.containsBadWord(request.content());
        CommentStatus status = containsBadWord ? CommentStatus.REJECTED : CommentStatus.PUBLISHED;

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthorId(authorId);
        comment.setParentCommentId(request.parentCommentId());
        comment.setContent(request.content());
        comment.setStatus(status);

        Comment savedComment = commentRepository.save(comment);

        if (status == CommentStatus.REJECTED) {
            log.warn("🚫 Yorum reddedildi (kötü kelime tespit edildi) — commentId: {}, postId: {}", savedComment.getId(), postId);
        } else {
            log.info("💬 Yorum oluşturuldu — commentId: {}, postId: {}, authorId: {}", savedComment.getId(), postId, authorId);
        }

        UserSummaryDto user = fetchUserSafely(authorId);
        return mapToResponse(savedComment, user, Collections.emptyList());
    }

    /**
     * Yorumu siler.
     * Sadece yorum yazarı silebilir.
     */
    @Transactional
    public void deleteComment(UUID postId, UUID commentId, UUID authorId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("Yorum bulunamadı: " + commentId));

        if (!comment.getPostId().equals(postId)) {
            throw new IllegalArgumentException("Yorum bu post'a ait değil.");
        }

        if (!comment.getAuthorId().equals(authorId)) {
            throw new UnauthorizedPostAccessException(
                    "Bu yorumu sadece yazarı silebilir. commentId: " + commentId);
        }

        commentRepository.delete(comment);
        log.info("🗑️ Yorum silindi — commentId: {}, postId: {}, authorId: {}", commentId, postId, authorId);
    }

    /**
     * Post'a ait yayınlanmış üst seviye yorumları sayfalayarak döndürür.
     * Her üst yorumun yanıtları (replies) da eklenir.
     *
     * N+1 optimizasyonu:
     * - Üst yorumlar tek sorgu ile çekilir.
     * - Benzersiz authorId'ler toplanıp batch olarak user bilgileri çekilir.
     * - Her üst yorum için yanıtlar ayrı sorgu ile alınır (sayfa başına yorum sayısı sınırlı olduğu için kabul edilebilir).
     */
    @Transactional(readOnly = true)
    public Page<CommentResponse> getCommentsByPostId(UUID postId, Pageable pageable) {
        // Post var mı kontrol et
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException("Post bulunamadı: " + postId);
        }

        Page<Comment> topLevelComments = commentRepository
                .findByPostIdAndStatusAndParentCommentIdIsNull(postId, CommentStatus.PUBLISHED, pageable);

        // Tüm üst yorum ve yanıtlardaki benzersiz authorId'leri topla
        List<UUID> allAuthorIds = topLevelComments.getContent().stream()
                .map(Comment::getAuthorId)
                .collect(Collectors.toList());

        // Her üst yorum için yanıtları çek ve author ID'lerini topla
        Map<UUID, List<Comment>> repliesMap = topLevelComments.getContent().stream()
                .collect(Collectors.toMap(
                        Comment::getId,
                        comment -> commentRepository.findByParentCommentIdAndStatus(
                                comment.getId(), CommentStatus.PUBLISHED)
                ));

        repliesMap.values().stream()
                .flatMap(List::stream)
                .map(Comment::getAuthorId)
                .forEach(allAuthorIds::add);

        // Benzersiz author bilgilerini batch olarak çek
        Map<UUID, UserSummaryDto> userCache = allAuthorIds.stream()
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        this::fetchUserSafely,
                        (existing, replacement) -> existing
                ));

        return topLevelComments.map(comment -> {
            List<Comment> replies = repliesMap.getOrDefault(comment.getId(), Collections.emptyList());
            List<CommentResponse> replyResponses = replies.stream()
                    .map(reply -> mapToResponse(reply, userCache.get(reply.getAuthorId()), Collections.emptyList()))
                    .toList();
            return mapToResponse(comment, userCache.get(comment.getAuthorId()), replyResponses);
        });
    }

    /**
     * Bir post'a ait yayınlanmış yorum sayısını döndürür.
     */
    public long getPublishedCommentCount(UUID postId) {
        return commentRepository.countByPostIdAndStatus(postId, CommentStatus.PUBLISHED);
    }

    /**
     * Bir yoruma yanıt oluşturur.
     * parentComment üst seviye yorum olmalıdır — yanıta yanıt verilemez.
     * İçerik blacklist kontrolünden geçer.
     */
    @Transactional
    public CommentResponse createReply(UUID parentCommentId, String content, UUID authorId) {
        Comment parentComment = commentRepository.findById(parentCommentId)
                .orElseThrow(() -> new CommentNotFoundException("Yanıt verilecek yorum bulunamadı: " + parentCommentId));

        // Yanıta yanıt verilemez — sadece üst seviye yorumlara yanıt verilebilir
        if (parentComment.getParentCommentId() != null) {
            throw new IllegalArgumentException(
                    "Yanıta yanıt verilemez. Sadece üst seviye yorumlara yanıt verilebilir.");
        }

        // Blacklist kontrolü
        boolean containsBadWord = blacklistProvider.containsBadWord(content);
        CommentStatus status = containsBadWord ? CommentStatus.REJECTED : CommentStatus.PUBLISHED;

        Comment reply = new Comment();
        reply.setPostId(parentComment.getPostId());
        reply.setAuthorId(authorId);
        reply.setParentCommentId(parentCommentId);
        reply.setContent(content);
        reply.setStatus(status);

        Comment savedReply = commentRepository.save(reply);

        if (status == CommentStatus.REJECTED) {
            log.warn("🚫 Yanıt reddedildi (kötü kelime tespit edildi) — replyId: {}, parentId: {}", savedReply.getId(), parentCommentId);
        } else {
            log.info("↩️ Yanıt oluşturuldu — replyId: {}, parentId: {}, authorId: {}", savedReply.getId(), parentCommentId, authorId);
        }

        UserSummaryDto user = fetchUserSafely(authorId);
        return mapToResponse(savedReply, user, Collections.emptyList());
    }

    /**
     * Bir yorumun yayınlanmış yanıtlarını döndürür.
     */
    @Transactional(readOnly = true)
    public List<CommentResponse> getRepliesByCommentId(UUID commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new CommentNotFoundException("Yorum bulunamadı: " + commentId);
        }

        List<Comment> replies = commentRepository.findByParentCommentIdAndStatus(commentId, CommentStatus.PUBLISHED);

        // Benzersiz author bilgilerini batch olarak çek
        Map<UUID, UserSummaryDto> userCache = replies.stream()
                .map(Comment::getAuthorId)
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        this::fetchUserSafely,
                        (existing, replacement) -> existing
                ));

        return replies.stream()
                .map(reply -> mapToResponse(reply, userCache.get(reply.getAuthorId()), Collections.emptyList()))
                .toList();
    }

    // ═══════════════════════════════════════════════
    // PRIVATE HELPER METOTLAR
    // ═══════════════════════════════════════════════

    private CommentResponse mapToResponse(Comment comment, UserSummaryDto user, List<CommentResponse> replies) {
        String authorName = null;
        if (user != null) {
            authorName = user.getFirstName() + " " + user.getLastName();
        }

        return new CommentResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getAuthorId(),
                authorName,
                comment.getParentCommentId(),
                comment.getContent(),
                comment.getStatus(),
                replies,
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }

    private UserSummaryDto fetchUserSafely(UUID userId) {
        try {
            return userClient.getUserById(userId);
        } catch (Exception e) {
            log.warn("⚠️ Kullanıcı bilgisi alınamadı — userId: {}", userId);
            return null;
        }
    }
}

