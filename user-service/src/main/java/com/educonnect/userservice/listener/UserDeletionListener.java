package com.educonnect.userservice.listener;

import com.educonnect.userservice.Repository.AcademicianRepository;
import com.educonnect.userservice.Repository.StudentRepository;
import com.educonnect.userservice.config.RabbitMQConfig;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
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
    private final StudentRepository studentRepository;
    private final AcademicianRepository academicianRepository;

    public UserDeletionListener(ProfileService profileService,
                                StudentRepository studentRepository,
                                AcademicianRepository academicianRepository) {
        this.profileService = profileService;
        this.studentRepository = studentRepository;
        this.academicianRepository = academicianRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.USER_DELETE_QUEUE)
    public void handleUserDeletion(UserDeletedMessage message) {
        LOGGER.info("Received user deletion message. UserID: {}, Type: {}, Reason: {}",
                message.getUserId(), message.getUserType(), message.getReason());

        if (message.getUserId() == null) {
            throw new AmqpRejectAndDontRequeueException("User deletion message without user id");
        }
        if ("STUDENT".equals(message.getUserType())) {
            if (!studentRepository.existsById(message.getUserId())) {
                LOGGER.info("Student profile already removed. UserID: {}", message.getUserId());
                return;
            }
            profileService.archiveStudent(message.getUserId(), message.getReason());
            LOGGER.info("Student archived successfully via deletion message. UserID: {}", message.getUserId());
        } else if ("ACADEMICIAN".equals(message.getUserType())) {
            if (!academicianRepository.existsById(message.getUserId())) {
                LOGGER.info("Academician profile already removed. UserID: {}", message.getUserId());
                return;
            }
            profileService.archiveAcademician(message.getUserId(), message.getReason());
            LOGGER.info("Academician archived successfully via deletion message. UserID: {}", message.getUserId());
        } else {
            throw new AmqpRejectAndDontRequeueException("Unknown user type in deletion message: " + message.getUserType());
        }
    }
}

