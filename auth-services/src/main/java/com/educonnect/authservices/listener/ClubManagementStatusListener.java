package com.educonnect.authservices.listener;

import com.educonnect.authservices.Repository.ClubManagementSyncRepository;
import com.educonnect.authservices.Repository.UserRepository;
import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.ClubManagementStatusChangedEvent;
import com.educonnect.authservices.models.ClubManagementSync;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Component
public class ClubManagementStatusListener {

    private static final Logger log = LoggerFactory.getLogger(ClubManagementStatusListener.class);

    private final UserRepository userRepository;
    private final ClubManagementSyncRepository syncRepository;

    public ClubManagementStatusListener(UserRepository userRepository, ClubManagementSyncRepository syncRepository) {
        this.userRepository = userRepository;
        this.syncRepository = syncRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.CLUB_MANAGEMENT_QUEUE)
    @Transactional
    public void handle(ClubManagementStatusChangedEvent event) {
        validate(event);

        Optional<ClubManagementSync> sync = syncRepository.findById(event.userId());
        if (sync.isPresent() && !event.occurredAt().isAfter(sync.get().getLastEventAt())) {
            log.info("Ignoring stale or duplicate club management event {} for user {}", event.eventId(), event.userId());
            return;
        }

        Optional<User> found = userRepository.findById(event.userId());
        if (found.isEmpty()) {
            log.info("Ignoring club management event {} for deleted user {}", event.eventId(), event.userId());
            return;
        }
        User user = found.get();

        Set<Role> roles = user.getRoles() != null ? new HashSet<>(user.getRoles()) : new HashSet<>();
        if (event.managesClub()) {
            roles.add(Role.ROLE_CLUB_OFFICIAL);
            roles.remove(Role.ROLE_PENDING_CLUB_OFFICIAL);
        } else {
            roles.remove(Role.ROLE_CLUB_OFFICIAL);
        }
        user.setRoles(roles);
        userRepository.save(user);

        if (sync.isPresent()) {
            sync.get().record(event.occurredAt(), event.eventId());
        } else {
            syncRepository.save(new ClubManagementSync(event.userId(), event.occurredAt(), event.eventId()));
        }

        log.info("Club management status applied: userId={}, managesClub={}", event.userId(), event.managesClub());
    }

    private static void validate(ClubManagementStatusChangedEvent event) {
        if (event == null || event.userId() == null || event.occurredAt() == null || event.eventId() == null) {
            throw new AmqpRejectAndDontRequeueException("Invalid club management event");
        }
        if (event.schemaVersion() != ClubManagementStatusChangedEvent.SUPPORTED_SCHEMA_VERSION
                || !ClubManagementStatusChangedEvent.EVENT_TYPE.equals(event.eventType())) {
            throw new AmqpRejectAndDontRequeueException("Unsupported club management event: "
                    + event.eventType() + " v" + event.schemaVersion());
        }
    }
}
