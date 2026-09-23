package com.educonnect.authservices.listener;

import com.educonnect.authservices.dto.message.AssignClubRoleMessage;
import com.educonnect.authservices.dto.message.RevokeClubRoleMessage;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.Repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

/**
 * RabbitMQ'dan gelen kulüp rolü atama mesajlarını dinler.
 */
@Component
public class ClubRoleAssignmentListener {

    private static final Logger log = LoggerFactory.getLogger(ClubRoleAssignmentListener.class);

    private static final Set<Role> MANAGEABLE_ROLES = Set.of(Role.ROLE_CLUB_OFFICIAL);

    private final UserRepository userRepository;

    public ClubRoleAssignmentListener(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @RabbitListener(queues = "user-role-assignment-queue")
    @Transactional
    public void handleRoleAssignment(AssignClubRoleMessage message) {
        log.info("Received role assignment message: userId={}, role={}, clubId={}",
                message.getUserId(), message.getClubRole(), message.getClubId());

        Role roleToAdd = resolveManageableRole(message.getClubRole());
        User user = findUser(message.getUserId());

        Set<Role> roles = user.getRoles() != null ? user.getRoles() : new HashSet<>();
        if (roles.add(roleToAdd)) {
            user.setRoles(roles);
            userRepository.save(user);
            log.info("Successfully added role {} to user {}", roleToAdd, message.getUserId());
        } else {
            log.info("User {} already has role {}", message.getUserId(), roleToAdd);
        }
    }

    @RabbitListener(queues = "user-role-revoke-queue")
    @Transactional
    public void handleRoleRevoke(RevokeClubRoleMessage message) {
        log.info("Received role revoke message: userId={}, role={}, clubId={}",
                message.getUserId(), message.getClubRole(), message.getClubId());

        Role roleToRemove = resolveManageableRole(message.getClubRole());
        User user = findUser(message.getUserId());

        Set<Role> roles = user.getRoles();
        if (roles != null && roles.remove(roleToRemove)) {
            user.setRoles(roles);
            userRepository.save(user);
            log.info("Successfully removed role {} from user {}", roleToRemove, message.getUserId());
        } else {
            log.info("User {} does not have role {}", message.getUserId(), roleToRemove);
        }
    }

    private Role resolveManageableRole(String roleName) {
        Role role;
        try {
            role = Role.valueOf(roleName);
        } catch (IllegalArgumentException | NullPointerException e) {
            log.error("Rejected role message: unknown role '{}'", roleName);
            throw new AmqpRejectAndDontRequeueException("Unknown role: " + roleName);
        }
        if (!MANAGEABLE_ROLES.contains(role)) {
            log.error("SECURITY: Rejected role message for non-manageable role '{}'", role);
            throw new AmqpRejectAndDontRequeueException("Role not manageable via queue: " + role);
        }
        return role;
    }

    private User findUser(java.util.UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> {
                    log.error("Rejected role message: user not found {}", userId);
                    return new AmqpRejectAndDontRequeueException("User not found: " + userId);
                });
    }
}
