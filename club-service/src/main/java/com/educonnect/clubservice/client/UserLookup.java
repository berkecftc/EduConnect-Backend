package com.educonnect.clubservice.client;

import com.educonnect.clubservice.dto.response.AcademicianSummary;
import com.educonnect.clubservice.dto.response.UserSummary;
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
public class UserLookup {

    private static final Logger log = LoggerFactory.getLogger(UserLookup.class);

    private final UserClient userClient;

    public UserLookup(UserClient userClient) {
        this.userClient = userClient;
    }

    public Map<UUID, UserSummary> usersById(Collection<UUID> userIds) {
        List<UUID> ids = distinct(userIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return userClient.getUsersByIds(ids).stream()
                    .filter(user -> user != null && user.getId() != null)
                    .collect(Collectors.toMap(UserSummary::getId, Function.identity(), (first, second) -> first));
        } catch (Exception e) {
            log.warn("Could not fetch {} user profiles: {}", ids.size(), e.getMessage());
            return Map.of();
        }
    }

    public Map<UUID, AcademicianSummary> academiciansById(Collection<UUID> userIds) {
        List<UUID> ids = distinct(userIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            return userClient.getAcademiciansByIds(ids).stream()
                    .filter(academician -> academician != null && academician.getId() != null)
                    .collect(Collectors.toMap(AcademicianSummary::getId, Function.identity(), (first, second) -> first));
        } catch (Exception e) {
            log.warn("Could not fetch {} academician profiles: {}", ids.size(), e.getMessage());
            return Map.of();
        }
    }

    private static List<UUID> distinct(Collection<UUID> userIds) {
        if (userIds == null) {
            return List.of();
        }
        return userIds.stream().filter(Objects::nonNull).distinct().toList();
    }
}
