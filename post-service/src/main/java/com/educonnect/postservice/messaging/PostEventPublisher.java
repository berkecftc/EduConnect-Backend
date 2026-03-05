package com.educonnect.postservice.messaging;

import com.educonnect.postservice.config.RabbitMQConfig;
import com.educonnect.postservice.event.PostModerationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Post moderasyon olaylarını RabbitMQ'ya yayınlar.
 *
 * Mesaj kaybı önlemi:
 * - Service katmanında @Transactional içinde DB kayıt yapıldıktan SONRA çağrılır.
 * - Jackson2JsonMessageConverter ile JSON serileştirme kullanılır.
 * - Kuyruk durable olduğu için broker yeniden başlasa bile mesaj korunur.
 */
@Service
public class PostEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PostEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public PostEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishModerationEvent(PostModerationEvent event) {
        log.info("📤 Moderasyon olayı yayınlanıyor — postId: {}, eventId: {}", event.getPostId(), event.getEventId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.POST_MODERATION_EXCHANGE,
                RabbitMQConfig.POST_MODERATION_ROUTING_KEY,
                event
        );
    }
}

