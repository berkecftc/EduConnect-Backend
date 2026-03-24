package com.educonnect.gamificationservice.listener;

import com.educonnect.gamificationservice.config.RabbitMQConfig;
import com.educonnect.gamificationservice.dto.event.GamificationEvent;
import com.educonnect.gamificationservice.service.GamificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class GamificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(GamificationEventListener.class);

    private final GamificationService gamificationService;

    public GamificationEventListener(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @RabbitListener(
            queues = RabbitMQConfig.GAMIFICATION_POINTS_QUEUE,
            containerFactory = "rabbitListenerContainerFactory"
    )
    public void consumeGamificationEvent(GamificationEvent event) {
        try {
            gamificationService.processEvent(event);
        } catch (Exception ex) {
            log.error("Failed to process gamification event. userId={}, actionType={}, referenceId={}",
                    event != null ? event.getUserId() : null,
                    event != null ? event.getActionType() : null,
                    event != null ? event.getReferenceId() : null,
                    ex);
            throw ex;
        }
    }
}


