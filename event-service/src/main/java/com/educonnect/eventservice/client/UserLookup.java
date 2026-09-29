package com.educonnect.eventservice.client;

import com.educonnect.eventservice.dto.response.UserSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class UserLookup {

    private static final Logger log = LoggerFactory.getLogger(UserLookup.class);

    private UserLookup() {
    }

    public static Map<UUID, UserSummary> usersById(UserClient userClient, Collection<UUID> userIds) {
        List<UUID> ids = userIds == null ? List.of()
                : userIds.stream().filter(Objects::nonNull).distinct().toList();
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
}
