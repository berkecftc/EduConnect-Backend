package com.educonnect.gamificationservice.listener;

import com.educonnect.gamificationservice.config.RabbitMQConfig;
import com.educonnect.gamificationservice.dto.event.UserDeletedMessage;
import com.educonnect.gamificationservice.service.UserDataCleanupService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UserDeletionListener {

    private final UserDataCleanupService cleanupService;

    public UserDeletionListener(UserDataCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @RabbitListener(queues = RabbitMQConfig.USER_DELETED_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void onUserDeleted(UserDeletedMessage message) {
        if (message == null || message.userId() == null) {
            throw new AmqpRejectAndDontRequeueException("UserDeletedMessage without userId");
        }
        cleanupService.deleteUserData(message.userId());
    }
}
