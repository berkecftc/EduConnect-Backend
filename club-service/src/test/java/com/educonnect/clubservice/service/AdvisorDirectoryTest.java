package com.educonnect.clubservice.service;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.dto.response.AcademicianSummary;
import com.educonnect.common.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdvisorDirectoryTest {

    private final UserClient userClient = mock(UserClient.class);
    private final AdvisorDirectory directory = new AdvisorDirectory(userClient);

    @Test
    void advisorsAreAcademiciansButNotResearchAssistants() {
        UUID professor = UUID.randomUUID();
        UUID lecturer = UUID.randomUUID();
        UUID researchAssistant = UUID.randomUUID();
        UUID student = UUID.randomUUID();
        when(userClient.getAcademicianById(professor)).thenReturn(summary("Academician", "FACULTY_MEMBER"));
        when(userClient.getAcademicianById(lecturer)).thenReturn(summary("Academician", null));
        when(userClient.getAcademicianById(researchAssistant)).thenReturn(summary("Academician", "RESEARCH_ASSISTANT"));
        when(userClient.getAcademicianById(student)).thenReturn(summary("Student", null));

        assertThatCode(() -> directory.requireAcademician(professor)).doesNotThrowAnyException();
        assertThatCode(() -> directory.requireAcademician(lecturer)).doesNotThrowAnyException();
        assertThatThrownBy(() -> directory.requireAcademician(researchAssistant))
                .isInstanceOf(ApiException.class)
                .hasFieldOrPropertyWithValue("errorCode", "ADVISOR_NOT_ELIGIBLE");
        assertThatThrownBy(() -> directory.requireAcademician(student)).isInstanceOf(ResponseStatusException.class);
    }

    private static AcademicianSummary summary(String role, String staffCategory) {
        AcademicianSummary summary = new AcademicianSummary();
        summary.setRole(role);
        summary.setStaffCategory(staffCategory);
        return summary;
    }
}
