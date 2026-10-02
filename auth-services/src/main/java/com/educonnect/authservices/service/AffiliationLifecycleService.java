package com.educonnect.authservices.service;

import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.AffiliationStatusChangedMessage;
import com.educonnect.authservices.dto.request.AffiliationStatusRequest;
import com.educonnect.authservices.dto.response.AffiliationStatusView;
import com.educonnect.authservices.models.AccountType;
import com.educonnect.authservices.models.AffiliationStatusChange;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.StaffStatus;
import com.educonnect.authservices.models.StudentStatus;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.AffiliationStatusChangeRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Service
public class AffiliationLifecycleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AffiliationLifecycleService.class);

    private final UserRepository userRepository;
    private final AffiliationStatusChangeRepository changeRepository;
    private final OutboxPublisher outboxPublisher;
    private final UserAdministrationService userAdministrationService;
    private final AcademicianAssignmentGuard assignmentGuard;
    private final RefreshTokenService refreshTokenService;
    private final int closureGraceDays;
    private final Clock clock;

    @Autowired
    public AffiliationLifecycleService(UserRepository userRepository,
                                       AffiliationStatusChangeRepository changeRepository,
                                       OutboxPublisher outboxPublisher,
                                       UserAdministrationService userAdministrationService,
                                       AcademicianAssignmentGuard assignmentGuard,
                                       RefreshTokenService refreshTokenService,
                                       @Value("${educonnect.auth.lifecycle.closure-grace-days:0}") int closureGraceDays) {
        this(userRepository, changeRepository, outboxPublisher, userAdministrationService, assignmentGuard,
                refreshTokenService, closureGraceDays, Clock.systemDefaultZone());
    }

    AffiliationLifecycleService(UserRepository userRepository,
                                AffiliationStatusChangeRepository changeRepository,
                                OutboxPublisher outboxPublisher,
                                UserAdministrationService userAdministrationService,
                                AcademicianAssignmentGuard assignmentGuard,
                                RefreshTokenService refreshTokenService,
                                int closureGraceDays,
                                Clock clock) {
        this.userRepository = userRepository;
        this.changeRepository = changeRepository;
        this.outboxPublisher = outboxPublisher;
        this.userAdministrationService = userAdministrationService;
        this.assignmentGuard = assignmentGuard;
        this.refreshTokenService = refreshTokenService;
        this.closureGraceDays = closureGraceDays;
        this.clock = clock;
    }

    @Transactional
    public AffiliationStatusView changeStudentStatus(UUID userId, AffiliationStatusRequest request, String actor) {
        User user = user(userId);
        StudentStatus target = parse(StudentStatus.class, request.status());
        StudentStatus current = studentStatusOf(user);
        if (current == null || current.ended()) {
            throw new ConflictException("NO_STUDENT_AFFILIATION", "Hesapta süren bir öğrenci kaydı yok.");
        }
        if (current == target) {
            throw new ConflictException("STATUS_UNCHANGED", "Öğrenci zaten bu durumda.");
        }
        user.setStudentStatus(target);
        if (target.ended()) {
            user.getRoles().remove(Role.ROLE_STUDENT);
        }
        return apply(user, AffiliationStatusChangedMessage.STUDENT, current.name(), target.name(), target.ended(), request, actor);
    }

    @Transactional
    public AffiliationStatusView changeStaffStatus(UUID userId, AffiliationStatusRequest request, String actor) {
        User user = user(userId);
        StaffStatus target = parse(StaffStatus.class, request.status());
        StaffStatus current = staffStatusOf(user);
        if (current == null || current.ended()) {
            throw new ConflictException("NO_STAFF_AFFILIATION", "Hesapta süren bir personel kaydı yok.");
        }
        if (current == target) {
            throw new ConflictException("STATUS_UNCHANGED", "Personel zaten bu durumda.");
        }
        if (target.ended()) {
            assignmentGuard.requireNoActiveAssignments(userId);
            user.getRoles().remove(Role.ROLE_ACADEMICIAN);
        }
        user.setStaffStatus(target);
        return apply(user, AffiliationStatusChangedMessage.STAFF, current.name(), target.name(), target.ended(), request, actor);
    }

    @Transactional(readOnly = true)
    public AffiliationStatusView view(UUID userId) {
        User user = user(userId);
        return new AffiliationStatusView(userId, name(studentStatusOf(user)), name(staffStatusOf(user)), user.getClosureDueAt(),
                changeRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(AffiliationStatusView.Change::of).toList());
    }

    @Scheduled(cron = "0 15 4 * * ?")
    @Transactional
    public void closeDueAccounts() {
        for (User user : userRepository.findByClosureDueAtBefore(clock.instant())) {
            if (AccountType.of(user.getRoles()) == AccountType.UNKNOWN) {
                userAdministrationService.deleteUser(user.getId(), "Hesap kapanış süresi doldu");
                LOGGER.info("Account closed after grace period. UserID: {}", user.getId());
            } else {
                user.setClosureDueAt(null);
                userRepository.save(user);
            }
        }
    }

    private AffiliationStatusView apply(User user, String affiliation, String previous, String status, boolean ended,
                                        AffiliationStatusRequest request, String actor) {
        boolean closing = ended && AccountType.of(user.getRoles()) == AccountType.UNKNOWN;
        LocalDate effectiveDate = request.effectiveDate() != null ? request.effectiveDate() : LocalDate.now(clock);
        String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
        changeRepository.save(new AffiliationStatusChange(user.getId(), affiliation, previous, status, effectiveDate, reason, actor));
        userRepository.save(user);
        outboxPublisher.publish(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.USER_AFFILIATION_STATUS_ROUTING_KEY,
                new AffiliationStatusChangedMessage(user.getId(), affiliation, status, ended, closing, effectiveDate, reason));
        LOGGER.info("Affiliation status changed. UserID: {}, {} {} -> {}", user.getId(), affiliation, previous, status);
        AffiliationStatusView view = view(user.getId());
        if (closing) {
            if (closureGraceDays <= 0) {
                userAdministrationService.deleteUser(user.getId(), "Hesap kapandı: " + status);
            } else {
                user.setClosureDueAt(clock.instant().plus(Duration.ofDays(closureGraceDays)));
                refreshTokenService.revokeAllSessions(user.getId());
                userRepository.save(user);
                view = view(user.getId());
            }
        }
        return view;
    }

    private User user(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Kullanıcı bulunamadı."));
    }

    static StudentStatus studentStatusOf(User user) {
        if (user.getStudentStatus() != null) {
            return user.getStudentStatus();
        }
        return user.getRoles() != null && user.getRoles().contains(Role.ROLE_STUDENT) ? StudentStatus.ACTIVE : null;
    }

    static StaffStatus staffStatusOf(User user) {
        if (user.getStaffStatus() != null) {
            return user.getStaffStatus();
        }
        return user.getRoles() != null && user.getRoles().contains(Role.ROLE_ACADEMICIAN) ? StaffStatus.ACTIVE : null;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("INVALID_STATUS", "Geçersiz durum: " + value);
        }
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
