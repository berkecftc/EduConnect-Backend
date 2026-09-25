package com.educonnect.courseservice.listener;

import com.educonnect.courseservice.config.RabbitMQConfig;
import com.educonnect.courseservice.dto.message.UserDeletedMessage;
import com.educonnect.courseservice.service.UserDataCleanupService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class UserDeletionListener {

    private final UserDataCleanupService cleanupService;

    public UserDeletionListener(UserDataCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @RabbitListener(queues = RabbitMQConfig.USER_DELETED_QUEUE)
    public void onUserDeleted(UserDeletedMessage message) {
        if (message == null || message.userId() == null) {
            throw new AmqpRejectAndDontRequeueException("UserDeletedMessage without userId");
        }
        cleanupService.deleteUserData(message.userId());
    }
}
