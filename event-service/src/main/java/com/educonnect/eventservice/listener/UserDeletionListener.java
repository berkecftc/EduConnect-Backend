package com.educonnect.eventservice.listener;

import com.educonnect.eventservice.config.EventRabbitMQConfig;
import com.educonnect.eventservice.dto.message.UserDeletedMessage;
import com.educonnect.eventservice.service.UserDataCleanupService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UserDeletionListener {

    private final UserDataCleanupService cleanupService;

    public UserDeletionListener(UserDataCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @RabbitListener(queues = EventRabbitMQConfig.USER_DELETED_QUEUE)
    public void onUserDeleted(UserDeletedMessage message) {
        if (message == null || message.userId() == null) {
            throw new AmqpRejectAndDontRequeueException("UserDeletedMessage without userId");
        }
        cleanupService.deleteUserData(message.userId());
    }
}
