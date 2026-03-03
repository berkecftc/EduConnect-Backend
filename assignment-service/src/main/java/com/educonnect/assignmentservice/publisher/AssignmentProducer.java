package com.educonnect.assignmentservice.publisher;

import com.educonnect.assignmentservice.config.RabbitMQConfig;
import com.educonnect.assignmentservice.event.AssignmentNotificationEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class AssignmentProducer {

    private final RabbitTemplate rabbitTemplate;

    public AssignmentProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendAssignmentCreatedNotification(AssignmentNotificationEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.COURSE_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_ASSIGNMENT_CREATED,
                event
        );
    }
}

