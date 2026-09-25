package com.educonnect.assignmentservice.listener;

import com.educonnect.assignmentservice.config.RabbitMQConfig;
import com.educonnect.assignmentservice.event.CourseEvent;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.service.AssignmentFiles;
import com.educonnect.assignmentservice.service.AssignmentService;
import com.educonnect.assignmentservice.service.MinioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CourseEventListener {

    private static final Logger log = LoggerFactory.getLogger(CourseEventListener.class);

    private final AssignmentRepository assignmentRepository;
    private final AssignmentFiles assignmentFiles;
    private final MinioService minioService;

    public CourseEventListener(AssignmentRepository assignmentRepository,
                               AssignmentFiles assignmentFiles,
                               MinioService minioService) {
        this.assignmentRepository = assignmentRepository;
        this.assignmentFiles = assignmentFiles;
        this.minioService = minioService;
    }

    @RabbitListener(queues = RabbitMQConfig.ASSIGNMENT_QUEUE)
    @Transactional
    @CacheEvict(value = AssignmentService.STUDENT_ASSIGNMENTS, allEntries = true)
    public void handleCourseDeletedEvent(CourseEvent event) {
        List<String> files = assignmentFiles.of(assignmentRepository.findByCourseId(event.getCourseId()));
        assignmentRepository.deleteByCourseId(event.getCourseId());
        minioService.deleteFilesAfterCommit(files);
        log.info("Silinen derse ait ödevler temizlendi: courseId={}, files={}", event.getCourseId(), files.size());
    }
}
