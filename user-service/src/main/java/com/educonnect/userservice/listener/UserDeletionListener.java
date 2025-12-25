package com.educonnect.userservice.listener;

import com.educonnect.userservice.config.RabbitMQConfig;
import com.educonnect.userservice.dto.message.UserDeletedMessage;
import com.educonnect.userservice.service.ProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UserDeletionListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserDeletionListener.class);

    private final ProfileService profileService;

    public UserDeletionListener(ProfileService profileService) {
        this.profileService = profileService;
    }

    @RabbitListener(queues = RabbitMQConfig.USER_DELETE_QUEUE)
    public void handleUserDeletion(UserDeletedMessage message) {
        LOGGER.info("Received user deletion message. UserID: {}, Type: {}, Reason: {}",
                message.getUserId(), message.getUserType(), message.getReason());

        try {
            if ("STUDENT".equals(message.getUserType())) {
                profileService.archiveStudent(message.getUserId(), message.getReason());
                LOGGER.info("Student archived successfully via deletion message. UserID: {}", message.getUserId());
            } else if ("ACADEMICIAN".equals(message.getUserType())) {
                profileService.archiveAcademician(message.getUserId(), message.getReason());
                LOGGER.info("Academician archived successfully via deletion message. UserID: {}", message.getUserId());
            } else {
                LOGGER.warn("Unknown user type in deletion message: {}. UserID: {}",
                    message.getUserType(), message.getUserId());
            }
        } catch (RuntimeException e) {
            LOGGER.error("Failed to archive user. UserID: {}, Type: {}, Error: {}",
                    message.getUserId(), message.getUserType(), e.getMessage());
            // Hata durumunda mesajı DLQ'ya gönderebiliriz veya yeniden deneme yapabiliriz
        }
    }
}

