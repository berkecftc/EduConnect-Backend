package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.client.InternalUserClient;
import com.educonnect.assignmentservice.client.UserClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class StudentDirectory {

    private static final Logger log = LoggerFactory.getLogger(StudentDirectory.class);

    private final InternalUserClient internalUserClient;

    public StudentDirectory(InternalUserClient internalUserClient) {
        this.internalUserClient = internalUserClient;
    }

    public Map<UUID, UserClient.UserProfileDTO> byId(Collection<UUID> studentIds) {
        List<UUID> ids = studentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return internalUserClient.getUsersByIds(ids).stream()
                    .filter(profile -> profile != null && profile.getId() != null)
                    .collect(Collectors.toMap(UserClient.UserProfileDTO::getId, Function.identity(), (first, second) -> first));
        } catch (Exception e) {
            log.warn("Could not fetch {} student profiles: {}", ids.size(), e.getMessage());
            return Map.of();
        }
    }
}
