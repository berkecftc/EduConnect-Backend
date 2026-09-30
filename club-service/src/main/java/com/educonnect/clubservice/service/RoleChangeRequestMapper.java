package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.response.RoleChangeRequestDTO;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
class RoleChangeRequestMapper {

    private final RoleChangeUserNames userNames;

    RoleChangeRequestMapper(RoleChangeUserNames userNames) {
        this.userNames = userNames;
    }

    RoleChangeRequestDTO toDto(ClubApprovalRequest request, Club club) {
        Map<UUID, String> names = new HashMap<>();
        names.put(request.getSubjectUserId(), userNames.nameOf(request.getSubjectUserId()));
        names.put(request.getPreparedBy(), userNames.nameOf(request.getPreparedBy()));
        return toDto(request, club, names);
    }

    List<RoleChangeRequestDTO> toDtos(List<ClubApprovalRequest> requests, Function<ClubApprovalRequest, Club> clubOf) {
        Map<UUID, String> names = userNames.namesOf(requests);
        return requests.stream()
                .map(request -> toDto(request, clubOf.apply(request), names))
                .collect(Collectors.toList());
    }

    private RoleChangeRequestDTO toDto(ClubApprovalRequest request, Club club, Map<UUID, String> names) {
        RoleChangeRequestDTO dto = new RoleChangeRequestDTO();
        dto.setId(request.getId());
        dto.setClubId(request.getClubId());
        dto.setClubName(club != null ? club.getName() : null);
        dto.setStudentId(request.getSubjectUserId());
        dto.setStudentName(names.getOrDefault(request.getSubjectUserId(), RoleChangeUserNames.UNKNOWN_USER_NAME));
        dto.setCurrentRole(request.getCurrentPosition());
        dto.setRequestedRole(request.getRequestedPosition());
        dto.setRequesterId(request.getPreparedBy());
        dto.setRequesterName(names.getOrDefault(request.getPreparedBy(), RoleChangeUserNames.UNKNOWN_USER_NAME));
        dto.setStatus(request.getStatus().toLegacy());
        dto.setRejectionReason(request.getRejectionReason());
        dto.setCreatedAt(LocalDateTime.ofInstant(request.getCreatedAt(), ZoneOffset.UTC));
        dto.setProcessedAt(request.getDecidedAt() != null ? LocalDateTime.ofInstant(request.getDecidedAt(), ZoneOffset.UTC) : null);
        return dto;
    }
}
