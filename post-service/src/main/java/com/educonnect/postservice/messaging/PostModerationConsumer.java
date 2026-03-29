package com.educonnect.postservice.messaging;

import com.educonnect.postservice.config.RabbitMQConfig;
import com.educonnect.postservice.event.ActionType;
import com.educonnect.postservice.event.GamificationEvent;
import com.educonnect.postservice.event.PostModerationEvent;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.PostRepository;
import com.educonnect.postservice.util.BlacklistProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

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

    private final PostRepository postRepository;
    private final BlacklistProvider blacklistProvider;
    private final RabbitTemplate rabbitTemplate;

    public PostModerationConsumer(PostRepository postRepository,
                                  BlacklistProvider blacklistProvider,
                                  RabbitTemplate rabbitTemplate) {
        this.postRepository = postRepository;
        this.blacklistProvider = blacklistProvider;
        this.rabbitTemplate = rabbitTemplate;
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
        String combinedText = event.getTitle() + " " + event.getContent();
        boolean containsBadWord = blacklistProvider.containsBadWord(combinedText);

        if (containsBadWord) {
            post.setStatus(PostStatus.REJECTED);
            postRepository.save(post);
            log.warn("🚫 Post reddedildi (kötü kelime tespit edildi) — postId: {}", post.getId());
        } else {
            post.setStatus(PostStatus.PUBLISHED);
            postRepository.save(post);
            log.info("✅ Post yayınlandı — postId: {}", post.getId());

            if (post.getCategory() == PostCategory.DERS_NOTU) {
                publishGamificationEventSafely(post);
            }
        }
    }

    private void publishGamificationEventSafely(Post post) {
        GamificationEvent gamificationEvent = new GamificationEvent(
                post.getAuthorId(),
                ActionType.POST_PUBLISHED,
                post.getId().toString(),
                OffsetDateTime.now()
        );

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.GAMIFICATION_EXCHANGE,
                    RabbitMQConfig.ROUTING_KEY_GAMIFICATION_POST_PUBLISHED,
                    gamificationEvent
            );
            log.info("🎯 Gamification olayı yayınlandı — postId: {}", post.getId());
        } catch (Exception ex) {
            // Moderasyon sonucu commit edilmeli; gamification publish hatası post'u tekrar PENDING yapmamalı.
            log.error("❌ Gamification olayı yayınlanamadı, moderasyon sonucu korunuyor — postId: {}", post.getId(), ex);
        }
    }
}

