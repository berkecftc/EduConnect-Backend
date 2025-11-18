package com.educonnect.userservice.listener;

import com.educonnect.userservice.config.RabbitMQConfig;
import com.educonnect.userservice.dto.message.AcademicianProfileMessage;
import com.educonnect.userservice.dto.message.UserRegisteredMessage;
import com.educonnect.userservice.models.Academician;
import com.educonnect.userservice.models.Student;
import com.educonnect.userservice.Repository.AcademicianRepository;
import com.educonnect.userservice.Repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ProfileCreationListener {

    private final StudentRepository studentRepository;
    private final AcademicianRepository academicianRepository;

    public ProfileCreationListener(StudentRepository studentRepository, AcademicianRepository academicianRepository) {
        this.studentRepository = studentRepository;
        this.academicianRepository = academicianRepository;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfileCreationListener.class);

    @RabbitListener(queues = RabbitMQConfig.QUEUE_NAME,
            autoStartup = "${user.listener.auto-start:true}",
            containerFactory = "rabbitListenerContainerFactory")
    public void handleProfileCreation(UserRegisteredMessage message) {

        LOGGER.info("Received new user registration message for user ID: {} | roles: {} | dept: {} | studentNo: {}",
                message.getUserId(), message.getRoles(), message.getDepartment(), message.getStudentNumber());

        try {
            if (message.getRoles() != null && message.getRoles().contains("ROLE_STUDENT")) {
                // Guvenli degerler
                String firstName = message.getFirstName();
                if (firstName == null || firstName.isBlank()) firstName = "Student";
                else firstName = firstName.trim();
                String lastName = message.getLastName();
                if (lastName == null || lastName.isBlank()) lastName = "User";
                else lastName = lastName.trim();

                Student newStudent = new Student();
                newStudent.setId(message.getUserId());
                newStudent.setFirstName(firstName);
                newStudent.setLastName(lastName);
                // Ek alanlar
                newStudent.setStudentNumber(message.getStudentNumber());
                newStudent.setDepartment(message.getDepartment());

                studentRepository.save(newStudent);
                LOGGER.info("Student profile created successfully for user ID: {} | dept: {} | studentNo: {}",
                        newStudent.getId(), newStudent.getDepartment(), newStudent.getStudentNumber());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create student profile for user ID: {}. Error: {}", message.getUserId(), e.getMessage());
        }
    }

    // --- YENİ METOT (Akademisyen kaydı için) ---
    // (Bu kuyruk, auth-service'in mesaj gönderdiği 'profile.academician.create'
    // routing key'ine bağlı olmalıdır. RabbitMQConfig'de ayarlanmalı.)
    @RabbitListener(queues = RabbitMQConfig.ACADEMICIAN_QUEUE_NAME)
    public void handleAcademicianProfileCreation(AcademicianProfileMessage message) {

        LOGGER.info("Received new academician profile creation message for user ID: {}", message.getUserId());

        try {
            // Akademisyen profili oluştur
            Academician newAcademician = new Academician();

            // ID'yi auth-service'ten gelen ID ile set et (ÇOK ÖNEMLİ)
            newAcademician.setId(message.getUserId());

            // DTO'dan gelen TÜM BİLGİLERİ doldur
            newAcademician.setFirstName(message.getFirstName());
            newAcademician.setLastName(message.getLastName());
            newAcademician.setTitle(message.getTitle());
            newAcademician.setDepartment(message.getDepartment());
            newAcademician.setOfficeNumber(message.getOfficeNumber());
            // newAcademician.setActive(false); // (Opsiyonel: Aktivasyon için)

            academicianRepository.save(newAcademician);

            LOGGER.info("Academician profile created successfully (pending admin approval) for user ID: {}", newAcademician.getId());

        } catch (Exception e) {
            LOGGER.error("Failed to create academician profile for user ID: {}. Error: {}", message.getUserId(), e.getMessage());
        }
    }
}
