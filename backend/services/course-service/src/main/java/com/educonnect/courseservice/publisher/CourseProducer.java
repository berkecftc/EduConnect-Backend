package com.educonnect.courseservice.publisher;

import com.educonnect.courseservice.config.RabbitMQConfig;
import com.educonnect.courseservice.event.CourseEvent;
import com.educonnect.courseservice.event.CourseNotificationEvent;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;

@Service
public class CourseProducer {
    private final OutboxPublisher outboxPublisher;

    public CourseProducer(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    public void sendCourseDeletedEvent(CourseEvent event) {
        outboxPublisher.publish(RabbitMQConfig.COURSE_EXCHANGE, RabbitMQConfig.ROUTING_KEY_DELETED, event);
    }

    public void sendAnnouncementNotification(CourseNotificationEvent event) {
        outboxPublisher.publish(RabbitMQConfig.COURSE_EXCHANGE, RabbitMQConfig.ROUTING_KEY_ANNOUNCEMENT, event);
    }

    public void sendAssignmentNotification(CourseNotificationEvent event) {
        outboxPublisher.publish(RabbitMQConfig.COURSE_EXCHANGE, RabbitMQConfig.ROUTING_KEY_ASSIGNMENT_CREATED, event);
    }
}