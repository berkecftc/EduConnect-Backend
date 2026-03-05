package com.educonnect.postservice.messaging;

import com.educonnect.postservice.config.RabbitMQConfig;
import com.educonnect.postservice.event.PostModerationEvent;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

/**
 * RabbitMQ'dan PostModerationEvent'leri tüketen ve içerik moderasyonu yapan consumer.
 *
 * ═══════════════════════════════════════════════════════════════
 * IDEMPOTENCY (Eş Etkililik) Stratejisi:
 * ═══════════════════════════════════════════════════════════════
 * RabbitMQ "at-least-once" delivery garantisi verir; yani aynı mesaj
 * ağ hatası veya consumer crash'i nedeniyle birden fazla kez teslim edilebilir.
 *
 * Bu consumer idempotency'yi şu şekilde sağlar:
 * 1. Mesaj işlenmeden ÖNCE DB'den post'un mevcut statüsü okunur.
 * 2. Eğer status zaten PUBLISHED veya REJECTED ise → mesaj daha önce
 *    işlenmiş demektir, tekrar işlenmez (early return).
 * 3. Sadece status == PENDING olan post'lar moderasyon sürecinden geçer.
 *
 * Bu yaklaşım, ayrı bir "processed_events" tablosuna gerek kalmadan
 * entity'nin kendi durumu üzerinden doğal idempotency sağlar.
 * ═══════════════════════════════════════════════════════════════
 *
 * Moderasyon mantığı (Mock):
 * - Basit bir kötü kelime blacklist kontrolü yapılır.
 * - title + content birleşik metni blacklist'teki kelimelerle taranır.
 * - Eşleşme varsa → REJECTED, yoksa → PUBLISHED.
 */
@Component
public class PostModerationConsumer {

    private static final Logger log = LoggerFactory.getLogger(PostModerationConsumer.class);

    /**
     * Kötü kelime blacklist'i (mock).
     * Gerçek uygulamada bu bir DB tablosu, harici API veya AI servisi olabilir.
     */
    private static final Set<String> BLACKLIST = Set.of(
            "küfür", "hakaret", "spam", "reklam", "argo",
            "nefret", "şiddet", "taciz", "dolandırıcılık"
    );

    private final PostRepository postRepository;

    public PostModerationConsumer(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.POST_MODERATION_QUEUE)
    @Transactional
    public void handleModerationEvent(PostModerationEvent event) {
        log.info("📥 Moderasyon olayı alındı — postId: {}, eventId: {}", event.getPostId(), event.getEventId());

        Optional<Post> optionalPost = postRepository.findById(event.getPostId());

        if (optionalPost.isEmpty()) {
            log.warn("⚠️ Post bulunamadı, mesaj atlanıyor — postId: {}", event.getPostId());
            return;
        }

        Post post = optionalPost.get();

        // ═══ IDEMPOTENCY KONTROLÜ ═══
        // Post zaten moderasyondan geçmişse (PUBLISHED veya REJECTED),
        // bu mesaj yinelenen bir teslimat demektir — güvenle atla.
        if (post.getStatus() != PostStatus.PENDING) {
            log.info("🔁 Post zaten işlenmiş (status={}), idempotent atlanıyor — postId: {}", post.getStatus(), post.getId());
            return;
        }

        // ═══ MODERASYON MANTIGI (MOCK) ═══
        String combinedText = (event.getTitle() + " " + event.getContent()).toLowerCase();
        boolean containsBadWord = BLACKLIST.stream().anyMatch(combinedText::contains);

        if (containsBadWord) {
            post.setStatus(PostStatus.REJECTED);
            postRepository.save(post);
            log.warn("🚫 Post reddedildi (kötü kelime tespit edildi) — postId: {}", post.getId());
        } else {
            post.setStatus(PostStatus.PUBLISHED);
            postRepository.save(post);
            log.info("✅ Post yayınlandı — postId: {}", post.getId());
        }
    }
}

