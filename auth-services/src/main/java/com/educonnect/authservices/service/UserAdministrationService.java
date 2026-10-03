package com.educonnect.authservices.service;

import com.educonnect.authservices.dto.response.UserContact;
import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.UserDeletedMessage;
import com.educonnect.authservices.dto.response.UserSummaryDto;
import com.educonnect.authservices.models.AccountType;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.AcademicianRequestRepository;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserAdministrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserAdministrationService.class);

    private final UserRepository userRepository;
    private final AcademicianRequestRepository requestRepository;
    private final StudentRequestRepository studentRequestRepository;
    private final OutboxPublisher outboxPublisher;
    private final MinioService minioService;
    private final EmailVerificationService emailVerificationService;

    public UserAdministrationService(UserRepository userRepository,
                                     AcademicianRequestRepository requestRepository,
                                     StudentRequestRepository studentRequestRepository,
                                     OutboxPublisher outboxPublisher,
                                     MinioService minioService,
                                     EmailVerificationService emailVerificationService) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.studentRequestRepository = studentRequestRepository;
        this.outboxPublisher = outboxPublisher;
        this.minioService = minioService;
        this.emailVerificationService = emailVerificationService;
    }

    public List<UserSummaryDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> new UserSummaryDto(
                        user.getId(),
                        user.getEmail(),
                        user.getRoles().stream().map(Enum::name).collect(Collectors.toSet()),
                        user.getStatus() != null ? user.getStatus().name() : null,
                        user.getEmailVerifiedAt() != null
                ))
                .collect(Collectors.toList());
    }

    public List<String> getEmailsByUserIds(List<UUID> userIds) {
        return userRepository.findEmailsByIds(userIds);
    }

    public List<UserContact> getContacts(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return userRepository.findContactsByIds(new LinkedHashSet<>(userIds));
    }

    @Transactional
    public void deleteUser(UUID userId) {
        deleteUser(userId, "Admin tarafından silindi");
    }

    @Transactional
    public void deleteUser(UUID userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Kullanıcı bulunamadı"));
        if (user.getRoles().contains(Role.ROLE_ADMIN)
                && userRepository.findAllByRolesContaining(Role.ROLE_ADMIN).size() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Son admin hesabı silinemez.");
        }

        UserDeletedMessage.UserType userType = deletedUserType(user);

        UserDeletedMessage message = new UserDeletedMessage(
                userId,
                userType.name(),
                reason
        );

        outboxPublisher.publish(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.USER_DELETE_ROUTING_KEY,
                message
        );
        LOGGER.info("User deletion message queued. UserID: {}, Type: {}", userId, userType);

        requestRepository.findByUserId(userId).ifPresent(request -> {
            requestRepository.delete(request);
            String idCardImageUrl = request.getIdCardImageUrl();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    minioService.deleteIdCardImage(idCardImageUrl);
                }
            });
        });
        studentRequestRepository.findByEmail(user.getEmail()).ifPresent(studentRequestRepository::delete);
        emailVerificationService.discardTokens(user.getEmail());

        userRepository.deleteById(userId);
        LOGGER.info("User deleted from auth_db. UserID: {}", userId);
    }

    private static UserDeletedMessage.UserType deletedUserType(User user) {
        return switch (AccountType.of(user.getRoles())) {
            case ACADEMICIAN -> UserDeletedMessage.UserType.ACADEMICIAN;
            case STUDENT -> UserDeletedMessage.UserType.STUDENT;
            default -> UserDeletedMessage.UserType.UNKNOWN;
        };
    }
}
