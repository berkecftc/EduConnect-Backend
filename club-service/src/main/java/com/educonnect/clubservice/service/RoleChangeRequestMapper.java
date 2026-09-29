package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.response.RoleChangeRequestDTO;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.RoleChangeRequest;
import org.springframework.stereotype.Component;

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

    RoleChangeRequestDTO toDto(RoleChangeRequest request, Club club) {
        Map<UUID, String> names = new HashMap<>();
        names.put(request.getStudentId(), userNames.nameOf(request.getStudentId()));
        names.put(request.getRequesterId(), userNames.nameOf(request.getRequesterId()));
        return toDto(request, club, names);
    }

    List<RoleChangeRequestDTO> toDtos(List<RoleChangeRequest> requests, Function<RoleChangeRequest, Club> clubOf) {
        Map<UUID, String> names = userNames.namesOf(requests);
        return requests.stream()
                .map(request -> toDto(request, clubOf.apply(request), names))
                .collect(Collectors.toList());
    }

    private RoleChangeRequestDTO toDto(RoleChangeRequest request, Club club, Map<UUID, String> names) {
        RoleChangeRequestDTO dto = new RoleChangeRequestDTO();
        dto.setId(request.getId());
        dto.setClubId(request.getClubId());
        dto.setClubName(club != null ? club.getName() : null);
        dto.setStudentId(request.getStudentId());
        dto.setStudentName(names.getOrDefault(request.getStudentId(), RoleChangeUserNames.UNKNOWN_USER_NAME));
        dto.setCurrentRole(request.getCurrentRole());
        dto.setRequestedRole(request.getRequestedRole());
        dto.setRequesterId(request.getRequesterId());
        dto.setRequesterName(names.getOrDefault(request.getRequesterId(), RoleChangeUserNames.UNKNOWN_USER_NAME));
        dto.setStatus(request.getStatus());
        dto.setRejectionReason(request.getRejectionReason());
        dto.setCreatedAt(request.getCreatedAt());
        dto.setProcessedAt(request.getProcessedAt());
        return dto;
    }
}
