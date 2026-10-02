package com.educonnect.clubservice.listener;

import com.educonnect.clubservice.config.ClubRabbitMQConfig;
import com.educonnect.clubservice.dto.message.AffiliationStatusChangedMessage;
import com.educonnect.clubservice.service.ClubAffiliationService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AffiliationStatusListener {

    private final ClubAffiliationService affiliationService;

    public AffiliationStatusListener(ClubAffiliationService affiliationService) {
        this.affiliationService = affiliationService;
    }

    @RabbitListener(queues = ClubRabbitMQConfig.USER_AFFILIATION_STATUS_QUEUE)
    public void onStatusChanged(AffiliationStatusChangedMessage message) {
        if (message == null || message.userId() == null || message.affiliation() == null || message.status() == null) {
            throw new AmqpRejectAndDontRequeueException("Invalid affiliation status message");
        }
        if ("STUDENT".equals(message.affiliation())) {
            if (message.ended()) {
                affiliationService.studentEnded(message.userId());
            } else if ("ON_LEAVE".equals(message.status())) {
                affiliationService.studentOnLeave(message.userId());
            } else if ("ACTIVE".equals(message.status())) {
                affiliationService.studentResumed(message.userId());
            }
        } else if ("STAFF".equals(message.affiliation()) && message.ended()) {
            affiliationService.staffEnded(message.userId());
        }
    }
}
