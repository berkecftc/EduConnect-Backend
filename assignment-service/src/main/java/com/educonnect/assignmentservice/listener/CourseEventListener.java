package com.educonnect.assignmentservice.listener;

import com.educonnect.assignmentservice.config.RabbitMQConfig;
import com.educonnect.assignmentservice.event.CourseEvent;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CourseEventListener {

    private final AssignmentRepository assignmentRepository;

    public CourseEventListener(AssignmentRepository assignmentRepository) {
        this.assignmentRepository = assignmentRepository;
    }

    // 👇 KUYRUĞU DİNLEYEN METOT
    @RabbitListener(queues = RabbitMQConfig.ASSIGNMENT_QUEUE)
    @Transactional
    public void handleCourseDeletedEvent(CourseEvent event) {
        System.out.println("📢 RabbitMQ Mesajı Alındı: Ders Silindi -> " + event.getCourseId());

        // O derse ait tüm ödevleri veritabanından sil
        assignmentRepository.deleteByCourseId(event.getCourseId());

        System.out.println("✅ " + event.getTitle() + " dersine ait ödevler temizlendi.");
    }
}