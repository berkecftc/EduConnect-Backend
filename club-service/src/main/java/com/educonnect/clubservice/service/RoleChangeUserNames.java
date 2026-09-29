package com.educonnect.clubservice.service;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.model.RoleChangeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
class RoleChangeUserNames {

    static final String UNKNOWN_USER_NAME = "Bilinmeyen Kullanıcı";

    private static final Logger log = LoggerFactory.getLogger(RoleChangeUserNames.class);

    private final UserClient userClient;

    RoleChangeUserNames(UserClient userClient) {
        this.userClient = userClient;
    }

    String nameOf(UUID userId) {
        if (userId == null) {
            return UNKNOWN_USER_NAME;
        }
        try {
            UserSummary user = userClient.getUserById(userId);
            if (user != null) {
                return user.getFirstName() + " " + user.getLastName();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user name for userId={}: {}", userId, e.getMessage());
        }
        return UNKNOWN_USER_NAME;
    }

    Map<UUID, String> namesOf(List<RoleChangeRequest> requests) {
        List<UUID> userIds = requests.stream()
                .flatMap(request -> Stream.of(request.getStudentId(), request.getRequesterId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            return userClient.getUsersByIds(userIds).stream()
                    .filter(user -> user != null && user.getId() != null)
                    .collect(Collectors.toMap(UserSummary::getId,
                            user -> user.getFirstName() + " " + user.getLastName(),
                            (first, second) -> first));
        } catch (Exception e) {
            log.warn("Failed to fetch {} user names: {}", userIds.size(), e.getMessage());
            return Map.of();
        }
    }
}
