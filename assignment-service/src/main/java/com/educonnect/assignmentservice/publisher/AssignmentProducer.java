package com.educonnect.assignmentservice.publisher;

import com.educonnect.assignmentservice.config.RabbitMQConfig;
import com.educonnect.assignmentservice.event.AssignmentNotificationEvent;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;

@Service
public class AssignmentProducer {

    private final OutboxPublisher outboxPublisher;

    public AssignmentProducer(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    public void sendAssignmentCreatedNotification(AssignmentNotificationEvent event) {
        outboxPublisher.publish(
                RabbitMQConfig.COURSE_EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_ASSIGNMENT_CREATED,
                event
        );
    }
}

