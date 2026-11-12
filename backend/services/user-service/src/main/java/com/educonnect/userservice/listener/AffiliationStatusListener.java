package com.educonnect.userservice.listener;

import com.educonnect.userservice.config.RabbitMQConfig;
import com.educonnect.userservice.dto.message.AffiliationStatusChangedMessage;
import com.educonnect.userservice.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AffiliationStatusListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(AffiliationStatusListener.class);
    private static final String STAFF = "STAFF";

    private final ProfileService profileService;

    public AffiliationStatusListener(ProfileService profileService) {
        this.profileService = profileService;
    }

    @RabbitListener(queues = RabbitMQConfig.USER_AFFILIATION_STATUS_QUEUE)
    public void handleStatusChange(AffiliationStatusChangedMessage message) {
        if (message == null || message.userId() == null || message.affiliation() == null || message.status() == null) {
            throw new AmqpRejectAndDontRequeueException("Invalid affiliation status message");
        }
        String affiliation = STAFF.equals(message.affiliation()) ? ProfileService.ACADEMICIAN_AFFILIATION : message.affiliation();
        profileService.applyAffiliationStatus(message.userId(), affiliation, message.status(), message.ended(), message.accountClosing());
        LOGGER.info("Affiliation status applied. UserID: {}, {} -> {}", message.userId(), message.affiliation(), message.status());
    }
}
