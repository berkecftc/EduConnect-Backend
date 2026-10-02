package com.educonnect.authservices.service;

import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.AcademicianProfileMessage;
import com.educonnect.authservices.dto.message.UserAccountStatusMessage;
import com.educonnect.authservices.dto.message.UserRegisteredMessage;
import com.educonnect.authservices.dto.response.AcademicianRequestAdminView;
import com.educonnect.authservices.dto.response.StudentRequestAdminView;
import com.educonnect.authservices.models.AcademicianRegistrationRequest;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.StudentRegistrationRequest;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.AcademicianRequestRepository;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.BadRequestException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RegistrationApprovalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationApprovalService.class);

    private final UserRepository userRepository;
    private final AcademicianRequestRepository requestRepository;
    private final StudentRequestRepository studentRequestRepository;
    private final OutboxPublisher outboxPublisher;
    private final MinioService minioService;
    private final EmailVerificationService emailVerificationService;
    private final InstitutionPolicy institutionPolicy;

    public RegistrationApprovalService(UserRepository userRepository,
                                       AcademicianRequestRepository requestRepository,
                                       StudentRequestRepository studentRequestRepository,
                                       OutboxPublisher outboxPublisher,
                                       MinioService minioService,
                                       EmailVerificationService emailVerificationService,
                                       InstitutionPolicy institutionPolicy) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.studentRequestRepository = studentRequestRepository;
        this.outboxPublisher = outboxPublisher;
        this.minioService = minioService;
        this.emailVerificationService = emailVerificationService;
        this.institutionPolicy = institutionPolicy;
    }

    @Transactional
    public void approveStudent(Long requestId) {
        StudentRegistrationRequest req = studentRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Öğrenci başvuru formu bulunamadı!"));
        requireVerifiedEmail(req.getEmailVerifiedAt());
        if (req.getStudentNumber() != null) {
            institutionPolicy.requireStudentNumberAvailable(req.getStudentNumber(), req.getId());
        }

        Set<Role> roles = Stream.of(Role.ROLE_STUDENT).collect(Collectors.toSet());

        var user = new User(
                req.getEmail(),
                req.getPassword(),
                roles
        );
        user.setEmailVerifiedAt(req.getEmailVerifiedAt() != null ? req.getEmailVerifiedAt() : Instant.now());
        user.setStudentNumber(req.getStudentNumber());

        User savedUser = userRepository.save(user);

        Set<String> roleStrings = Stream.of(Role.ROLE_STUDENT.name()).collect(Collectors.toSet());

        UserRegisteredMessage message = new UserRegisteredMessage(
                savedUser.getId(),
                req.getFirstName(),
                req.getLastName(),
                req.getEmail(),
                roleStrings,
                req.getStudentNumber(),
                req.getDepartment(),
                req.getStudentDocumentUrl()
        );
        message.setProgramId(req.getProgramId());
        message.setEntryYear(req.getEntryYear());

        outboxPublisher.publish(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                message
        );

        publishAccountStatus(req.getEmail(), req.getFirstName(), req.getLastName(),
                UserAccountStatusMessage.Status.APPROVED, UserAccountStatusMessage.UserType.STUDENT, null);

        studentRequestRepository.delete(req);

        LOGGER.info("Öğrenci onaylandı ve profil oluşturma mesajı gönderildi. UserID: {}", savedUser.getId());
    }

    @Transactional
    public void rejectStudent(Long requestId, String rejectionReason) {
        StudentRegistrationRequest req = studentRequestRepository.findById(requestId)
                .orElseThrow(() -> new NoSuchElementException("Öğrenci başvuru formu bulunamadı!"));

        publishAccountStatus(req.getEmail(), req.getFirstName(), req.getLastName(),
                UserAccountStatusMessage.Status.REJECTED, UserAccountStatusMessage.UserType.STUDENT, rejectionReason);
        LOGGER.info("Öğrenci red bildirimi RabbitMQ'ya gönderildi. RoutingKey: {}", RabbitMQConfig.USER_ACCOUNT_STATUS_ROUTING_KEY);

        minioService.deleteStudentDocument(req.getStudentDocumentUrl());

        studentRequestRepository.delete(req);

        LOGGER.info("Öğrenci başvurusu reddedildi. RequestId: {}", req.getId());
    }

    public List<StudentRequestAdminView> getAllStudentRequests() {
        return studentRequestRepository.findAll().stream()
                .map(req -> new StudentRequestAdminView(
                        req.getId(),
                        req.getFirstName(),
                        req.getLastName(),
                        req.getEmail(),
                        req.getStudentNumber(),
                        req.getDepartment(),
                        minioService.createPresignedUrl(req.getStudentDocumentUrl()),
                        req.getEmailVerifiedAt() != null
                ))
                .toList();
    }

    @Transactional
    public void approveAcademician(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        requireVerifiedEmail(user.getEmailVerifiedAt());

        AcademicianRegistrationRequest req = requestRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Başvuru formu bulunamadı!"));

        Set<Role> roles = user.getRoles();
        if (roles.contains(Role.ROLE_PENDING_ACADEMICIAN)) {
            roles.remove(Role.ROLE_PENDING_ACADEMICIAN);
            roles.add(Role.ROLE_ACADEMICIAN);
            user.setRoles(roles);
            userRepository.save(user);
        } else {
            LOGGER.warn("Kullanıcı zaten PENDING rolünde değil veya işlem hatalı: {}", userId);
        }

        AcademicianProfileMessage profileMessage = new AcademicianProfileMessage(
                user.getId(),
                req.getFirstName(),
                req.getLastName(),
                user.getEmail(),
                req.getTitle(),
                req.getDepartment(),
                req.getOfficeNumber(),
                req.getIdCardImageUrl()
        );
        profileMessage.setDepartmentId(req.getDepartmentId());

        outboxPublisher.publish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ACADEMICIAN_ROUTING_KEY, profileMessage);

        publishAccountStatus(user.getEmail(), req.getFirstName(), req.getLastName(),
                UserAccountStatusMessage.Status.APPROVED, UserAccountStatusMessage.UserType.ACADEMICIAN, null);

        requestRepository.delete(req);

        LOGGER.info("Akademisyen onaylandı ve profil oluşturma mesajı gönderildi. UserID: {}", userId);
    }

    public List<AcademicianRequestAdminView> getAllAcademicianRequests() {
        return requestRepository.findAll().stream()
                .map(req -> new AcademicianRequestAdminView(
                        req.getId(),
                        req.getUserId(),
                        req.getFirstName(),
                        req.getLastName(),
                        req.getTitle(),
                        req.getDepartment(),
                        req.getOfficeNumber(),
                        minioService.createPresignedUrl(req.getIdCardImageUrl()),
                        userRepository.findById(req.getUserId())
                                .map(u -> u.getEmailVerifiedAt() != null)
                                .orElse(false)
                ))
                .toList();
    }

    @Transactional
    public void rejectAcademician(UUID userId, String rejectionReason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found"));

        AcademicianRegistrationRequest req = requestRepository.findByUserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Request not found"));

        Set<Role> roles = user.getRoles();
        if (!roles.contains(Role.ROLE_PENDING_ACADEMICIAN)) {
            throw new BadRequestException("NO_PENDING_ACADEMICIAN_REQUEST", "User does not have a pending academician request");
        }

        publishAccountStatus(user.getEmail(), req.getFirstName(), req.getLastName(),
                UserAccountStatusMessage.Status.REJECTED, UserAccountStatusMessage.UserType.ACADEMICIAN, rejectionReason);
        LOGGER.info("Akademisyen red bildirimi RabbitMQ'ya gönderildi. RoutingKey: {}", RabbitMQConfig.USER_ACCOUNT_STATUS_ROUTING_KEY);

        if (req.getIdCardImageUrl() != null) {
            minioService.deleteIdCardImage(req.getIdCardImageUrl());
        }

        requestRepository.delete(req);

        userRepository.delete(user);

        LOGGER.info("Akademisyen başvurusu reddedildi ve kullanıcı silindi. UserID: {}", userId);
    }

    private void publishAccountStatus(String email, String firstName, String lastName,
                                      UserAccountStatusMessage.Status status,
                                      UserAccountStatusMessage.UserType userType,
                                      String rejectionReason) {
        UserAccountStatusMessage statusMessage = new UserAccountStatusMessage(
                email,
                firstName,
                lastName,
                status.name(),
                userType.name(),
                rejectionReason
        );
        outboxPublisher.publish(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.USER_ACCOUNT_STATUS_ROUTING_KEY,
                statusMessage
        );
    }

    private void requireVerifiedEmail(Instant emailVerifiedAt) {
        if (!emailVerificationService.isVerified(emailVerifiedAt)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Başvuru sahibi e-posta adresini henüz doğrulamadı.");
        }
    }
}
