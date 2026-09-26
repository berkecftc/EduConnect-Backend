package com.educonnect.postservice.messaging;

import com.educonnect.postservice.config.RabbitMQConfig;
import com.educonnect.postservice.event.PostModerationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;

/**
 * Post moderasyon olaylarını RabbitMQ'ya yayınlar.
 *
 * Mesaj kaybı önlemi:
 * - Service katmanı event'i transaction commit olduktan sonra publish edecek şekilde çağırır.
 * - Jackson2JsonMessageConverter ile JSON serileştirme kullanılır.
 * - Kuyruk durable olduğu için broker yeniden başlasa bile mesaj korunur.
 */
@Service
public class PostEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PostEventPublisher.class);

    private final OutboxPublisher outboxPublisher;

    public PostEventPublisher(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    public void publishModerationEvent(PostModerationEvent event) {
        log.info("Moderasyon olayı yayınlanıyor — postId: {}, eventId: {}", event.getPostId(), event.getEventId());
        outboxPublisher.publish(
                RabbitMQConfig.POST_MODERATION_EXCHANGE,
                RabbitMQConfig.POST_MODERATION_ROUTING_KEY,
                event
        );
    }
}

