package com.educonnect.postservice.messaging;

import com.educonnect.postservice.config.RabbitMQConfig;
import com.educonnect.postservice.dto.ModerationDecision;
import com.educonnect.postservice.event.PostModerationEvent;
import com.educonnect.postservice.service.PostModerationService;
import com.educonnect.postservice.util.BlacklistProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
@ConditionalOnProperty(name = "post.moderation.mock-consumer.enabled", havingValue = "true", matchIfMissing = true)
@Component
public class PostModerationConsumer {

    private static final Logger log = LoggerFactory.getLogger(PostModerationConsumer.class);

    private final BlacklistProvider blacklistProvider;
    private final PostModerationService postModerationService;

    public PostModerationConsumer(BlacklistProvider blacklistProvider,
                                  PostModerationService postModerationService) {
        this.blacklistProvider = blacklistProvider;
        this.postModerationService = postModerationService;
    }

    @RabbitListener(queues = RabbitMQConfig.POST_MODERATION_QUEUE)
    @Transactional
    public void handleModerationEvent(PostModerationEvent event) {
        log.info("Moderation event received (mock). postId={}, eventId={}", event.getPostId(), event.getEventId());

        String combinedText = event.getTitle() + " " + event.getContent();
        boolean containsBadWord = blacklistProvider.containsBadWord(combinedText);

        ModerationDecision decision = containsBadWord ? ModerationDecision.ZORBA : ModerationDecision.TEMIZ;
        postModerationService.applyModeration(event.getPostId(), decision, event.getEventId());
    }
}
