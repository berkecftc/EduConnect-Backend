package com.educonnect.userservice.listener;

import com.educonnect.userservice.config.RabbitMQConfig;
import com.educonnect.userservice.dto.message.UserEmailChangedMessage;
import com.educonnect.userservice.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EmailChangeListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailChangeListener.class);

    private final ProfileService profileService;

    public EmailChangeListener(ProfileService profileService) {
        this.profileService = profileService;
    }

    @RabbitListener(queues = RabbitMQConfig.USER_EMAIL_CHANGED_QUEUE)
    public void handleEmailChange(UserEmailChangedMessage message) {
        if (message == null || message.userId() == null || message.email() == null || message.email().isBlank()) {
            throw new AmqpRejectAndDontRequeueException("Invalid email change message");
        }
        profileService.changeEmail(message.userId(), message.email());
        LOGGER.info("Profile email updated. UserID: {}", message.userId());
    }
}
