package com.educonnect.courseservice.publisher;

import com.educonnect.courseservice.config.RabbitMQConfig;
import com.educonnect.courseservice.event.CourseEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class CourseProducer {
    private final RabbitTemplate rabbitTemplate;

    public CourseProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendCourseCreatedEvent(CourseEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.COURSE_EXCHANGE, RabbitMQConfig.ROUTING_KEY_CREATED, event);
    }
    public void sendCourseDeletedEvent(CourseEvent event) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.COURSE_EXCHANGE, RabbitMQConfig.ROUTING_KEY_DELETED, event);
    }
}