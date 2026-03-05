package com.educonnect.postservice.service;

import com.educonnect.postservice.client.UserClient;
import com.educonnect.postservice.dto.CreatePostRequest;
import com.educonnect.postservice.dto.PostResponse;
import com.educonnect.postservice.dto.UpdatePostRequest;
import com.educonnect.postservice.dto.UserSummaryDto;
import com.educonnect.postservice.event.PostModerationEvent;
import com.educonnect.postservice.exception.PostNotFoundException;
import com.educonnect.postservice.exception.UnauthorizedPostAccessException;
import com.educonnect.postservice.messaging.PostEventPublisher;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Post iş mantığı katmanı.
 *
 * Yetki doğrulaması (Authorization) Controller'da değil burada yapılır.
 * authorId, Controller'daki X-Authenticated-User-Id header'ından alınıp buraya aktarılır.
 */
@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    /**
     * Blog sayfasına erişebilen roller.
     * Sadece öğrenci ve kulüp yetkilisi erişebilir.
     */
    private static final Set<String> ALLOWED_ROLES = Set.of(
            "ROLE_STUDENT",
            "ROLE_CLUB_OFFICIAL"
    );

    private final PostRepository postRepository;
    private final PostEventPublisher eventPublisher;
    private final UserClient userClient;

    public PostService(PostRepository postRepository, PostEventPublisher eventPublisher, UserClient userClient) {
        this.postRepository = postRepository;
        this.eventPublisher = eventPublisher;
        this.userClient = userClient;
    }

    /**
     * Rol bazlı erişim kontrolü.
     * API Gateway'den gelen X-Authenticated-User-Roles header'ı virgülle ayrılmış roller içerir.
     * Kullanıcının en az bir rolü ALLOWED_ROLES içinde olmalıdır.
     * Aksi halde 403 Forbidden fırlatılır.
     */
    public void validatePostAccess(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            throw new UnauthorizedPostAccessException("Blog sayfasına erişim yetkiniz bulunmamaktadır.");
        }

        boolean hasAccess = Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .anyMatch(ALLOWED_ROLES::contains);

        if (!hasAccess) {
            throw new UnauthorizedPostAccessException(
                    "Blog sayfasına sadece öğrenci ve kulüp yetkilileri erişebilir."
            );
        }
    }

    /**
     * Yeni post oluşturur.
     * - Status başlangıçta PENDING olarak kaydedilir.
     * - DB'ye kaydedildikten sonra moderasyon olayı RabbitMQ'ya fırlatılır.
     *
     * Mesaj kaybı analizi:
     * - @Transactional sayesinde DB kaydı garanti altındadır.
     * - Event, DB commit'inden sonra fırlatılır. Eğer publish sırasında hata olursa
     *   post PENDING kalır ve manuel moderasyon ile çözülebilir.
     * - Kuyruk durable olduğu için broker tarafında mesaj kaybı olmaz.
     */
    @Transactional
    public PostResponse createPost(CreatePostRequest request, UUID authorId) {
        Post post = new Post();
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setCategory(request.category());
        post.setStatus(PostStatus.PENDING);
        post.setAuthorId(authorId);

        Post savedPost = postRepository.save(post);
        log.info("📝 Post oluşturuldu (PENDING) — postId: {}, authorId: {}", savedPost.getId(), authorId);

        // Moderasyon olayını RabbitMQ'ya fırlat
        publishModerationEvent(savedPost);

        UserSummaryDto user = fetchUserSafely(authorId);
        return mapToResponseWithUser(savedPost, user);
    }

    /**
     * Mevcut post'u günceller.
     * - Sadece yazarın kendisi güncelleyebilir (Service katmanında kontrol).
     * - Güncelleme sonrası status tekrar PENDING'e çekilir ve yeni moderasyon olayı fırlatılır.
     */
    @Transactional
    public PostResponse updatePost(UUID postId, UpdatePostRequest request, UUID authorId) {
        Post post = findPostOrThrow(postId);
        validateAuthor(post, authorId);

        post.setTitle(request.title());
        post.setContent(request.content());
        post.setCategory(request.category());
        post.setStatus(PostStatus.PENDING); // Güncelleme sonrası tekrar moderasyona gider

        Post updatedPost = postRepository.save(post);
        log.info("✏️ Post güncellendi (PENDING) — postId: {}, authorId: {}", updatedPost.getId(), authorId);

        // Yeni moderasyon olayını RabbitMQ'ya fırlat
        publishModerationEvent(updatedPost);

        UserSummaryDto user = fetchUserSafely(authorId);
        return mapToResponseWithUser(updatedPost, user);
    }

    /**
     * Post'u siler.
     * - Sadece yazarın kendisi silebilir (Service katmanında kontrol).
     */
    @Transactional
    public void deletePost(UUID postId, UUID authorId) {
        Post post = findPostOrThrow(postId);
        validateAuthor(post, authorId);

        postRepository.delete(post);
        log.info("🗑️ Post silindi — postId: {}, authorId: {}", postId, authorId);
    }

    /**
     * Yayınlanmış postları sayfalayarak döndürür.
     * Sadece status=PUBLISHED olanlar listelenir.
     *
     * N+1 analizi:
     * - findByStatus tek bir SQL sorgusu çalıştırır.
     * - Yazar bilgileri için sayfadaki benzersiz authorId'ler toplanır ve
     *   her biri için tek tek user-service çağrısı yapılır, sonuçlar bir Map'te cache'lenir.
     * - Bu sayede aynı yazar birden fazla post yazmışsa tekrar çağrı yapılmaz.
     */
    @Transactional(readOnly = true)
    public Page<PostResponse> getPublishedPosts(Pageable pageable) {
        Page<Post> postPage = postRepository.findByStatus(PostStatus.PUBLISHED, pageable);

        // Sayfadaki benzersiz authorId'leri topla ve batch olarak user bilgilerini çek
        List<UUID> uniqueAuthorIds = postPage.getContent().stream()
                .map(Post::getAuthorId)
                .distinct()
                .toList();

        Map<UUID, UserSummaryDto> userCache = uniqueAuthorIds.stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        id -> fetchUserSafely(id),
                        (existing, replacement) -> existing
                ));

        return postPage.map(post -> mapToResponseWithUser(post, userCache.get(post.getAuthorId())));
    }

    /**
     * Tek bir post'u ID'sine göre getirir.
     */
    @Transactional(readOnly = true)
    public PostResponse getPostById(UUID postId) {
        Post post = findPostOrThrow(postId);
        UserSummaryDto user = fetchUserSafely(post.getAuthorId());
        return mapToResponseWithUser(post, user);
    }

    // ═══════════════════════════════════════════════
    // PRIVATE HELPER METOTLAR
    // ═══════════════════════════════════════════════

    private Post findPostOrThrow(UUID postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post bulunamadı: " + postId));
    }

    /**
     * Yetki kontrolü: Post'un yazarı ile isteği yapan kullanıcı eşleşmeli.
     * Eşleşmezse UnauthorizedPostAccessException fırlatılır.
     */
    private void validateAuthor(Post post, UUID authorId) {
        if (!post.getAuthorId().equals(authorId)) {
            throw new UnauthorizedPostAccessException(
                    "Bu işlemi sadece post'un yazarı yapabilir. postId: " + post.getId()
            );
        }
    }

    private void publishModerationEvent(Post post) {
        PostModerationEvent event = new PostModerationEvent(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                UUID.randomUUID() // Her olay için benzersiz eventId
        );
        eventPublisher.publishModerationEvent(event);
    }

    private PostResponse mapToResponseWithUser(Post post, UserSummaryDto user) {
        String authorName = null;
        String authorDepartment = null;

        if (user != null) {
            authorName = user.getFirstName() + " " + user.getLastName();
            authorDepartment = user.getDepartment();
        }

        return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getCategory(),
                post.getStatus(),
                post.getAuthorId(),
                authorName,
                authorDepartment,
                post.getCreatedAt(),
                post.getUpdatedAt()
        );
    }

    /**
     * user-service'ten kullanıcı bilgilerini güvenli şekilde çeker.
     * Servis erişilemezse veya hata olursa null döner — post response'u yine de oluşturulur.
     */
    private UserSummaryDto fetchUserSafely(UUID userId) {
        try {
            return userClient.getUserById(userId);
        } catch (Exception e) {
            log.warn("⚠️ Kullanıcı bilgisi alınamadı (user-service erişilemez olabilir) — userId: {}", userId);
            return null;
        }
    }
}

