package com.educonnect.authservices.listener;

import com.educonnect.authservices.dto.message.AssignClubRoleMessage;
import com.educonnect.authservices.dto.message.RevokeClubRoleMessage;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.Repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final UserRepository userRepository;

    public ClubRoleAssignmentListener(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @RabbitListener(queues = "user-role-assignment-queue")
    @Transactional
    public void handleRoleAssignment(AssignClubRoleMessage message) {
        log.info("Received role assignment message: userId={}, role={}, clubId={}",
                message.getUserId(), message.getClubRole(), message.getClubId());

        try {
            // Kullanıcıyı bul
            User user = userRepository.findById(message.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + message.getUserId()));

            // Rolü enum'a çevir
            Role roleToAdd;
            try {
                roleToAdd = Role.valueOf(message.getClubRole());
            } catch (IllegalArgumentException e) {
                log.error("Invalid role: {}", message.getClubRole());
                return;
            }

            // Kullanıcının mevcut rolleri
            Set<Role> roles = user.getRoles();
            if (roles == null) {
                roles = new HashSet<>();
            }

            // Rolü ekle (zaten varsa Set yapısı duplicate engeller)
            boolean added = roles.add(roleToAdd);

            if (added) {
                user.setRoles(roles);
                userRepository.save(user);
                log.info("Successfully added role {} to user {}", roleToAdd, message.getUserId());
            } else {
                log.info("User {} already has role {}", message.getUserId(), roleToAdd);
            }

        } catch (Exception e) {
            log.error("Failed to assign role: {}", e.getMessage(), e);
            // İsterseniz burada dead letter queue'ya yönlendirebilirsiniz
            throw e; // RabbitMQ retry mekanizması için
        }
    }

    @RabbitListener(queues = "user-role-revoke-queue")
    @Transactional
    public void handleRoleRevoke(RevokeClubRoleMessage message) {
        log.info("Received role revoke message: userId={}, role={}, clubId={}",
                message.getUserId(), message.getClubRole(), message.getClubId());

        try {
            // Kullanıcıyı bul
            User user = userRepository.findById(message.getUserId())
                    .orElseThrow(() -> new RuntimeException("User not found with id: " + message.getUserId()));

            // Rolü enum'a çevir
            Role roleToRemove;
            try {
                roleToRemove = Role.valueOf(message.getClubRole());
            } catch (IllegalArgumentException e) {
                log.error("Invalid role: {}", message.getClubRole());
                return;
            }

            // Kullanıcının mevcut rolleri
            Set<Role> roles = user.getRoles();
            if (roles == null || roles.isEmpty()) {
                log.warn("User {} has no roles to revoke", message.getUserId());
                return;
            }

            // Rolü kaldır
            boolean removed = roles.remove(roleToRemove);

            if (removed) {
                user.setRoles(roles);
                userRepository.save(user);
                log.info("Successfully removed role {} from user {}", roleToRemove, message.getUserId());
            } else {
                log.info("User {} does not have role {}", message.getUserId(), roleToRemove);
            }

        } catch (Exception e) {
            log.error("Failed to revoke role: {}", e.getMessage(), e);
            throw e; // RabbitMQ retry mekanizması için
        }
    }
}
