package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubCreationRequest;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClubResponsesTest {

    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    @Test
    void clubResponse_keepsEntityFieldNamesWithoutVersion() throws Exception {
        Club club = new Club();
        club.setId(UUID.randomUUID());
        club.setName("Yazılım Kulübü");
        club.setAcademicAdvisorId(UUID.randomUUID());

        Map<String, Object> json = objectMapper.readValue(objectMapper.writeValueAsString(ClubResponse.from(club)), JSON_MAP);

        assertThat(json.keySet()).containsExactlyInAnyOrder("id", "name", "about", "logoUrl", "academicAdvisorId",
                "createdAt", "updatedAt", "status", "profile");
    }

    @Test
    void creationRequestResponse_exposesRequestDate() throws Exception {
        ClubCreationRequest request = new ClubCreationRequest();
        request.setId(UUID.randomUUID());
        request.setClubName("Fotoğrafçılık Kulübü");
        request.setRequestingStudentId(UUID.randomUUID());

        Map<String, Object> json = objectMapper.readValue(objectMapper.writeValueAsString(ClubCreationRequestResponse.from(request, List.of())), JSON_MAP);

        assertThat(json.keySet()).containsExactlyInAnyOrder("id", "clubName", "about", "requestingStudentId",
                "suggestedAdvisorId", "status", "requestDate", "rejectionReason", "processedAt", "processedBy", "clubId", "founders");
        assertThat(json.get("requestDate")).isNotNull();
        assertThat(json.get("status")).isEqualTo("PENDING");
    }
}
