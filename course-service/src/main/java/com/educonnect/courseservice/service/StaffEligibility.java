package com.educonnect.courseservice.service;

import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.dto.UserSummaryDto;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class StaffEligibility {

    private static final String ACADEMICIAN = "Academician";
    private static final String RESEARCH_ASSISTANT = "RESEARCH_ASSISTANT";

    private final UserClient userClient;

    public StaffEligibility(UserClient userClient) {
        this.userClient = userClient;
    }

    public UserSummaryDto requireAcademician(UUID userId) {
        UserSummaryDto user;
        try {
            user = userClient.getUserById(userId);
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("USER_NOT_FOUND", "Kullanıcı bulunamadı.");
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Kullanıcı bilgisi şu an doğrulanamıyor. Biraz sonra tekrar deneyin.", e);
        }
        if (user == null || !ACADEMICIAN.equalsIgnoreCase(user.getRole())) {
            throw new ConflictException("STAFF_NOT_ACADEMICIAN", "Ders kadrosunda yalnızca akademisyenler yer alabilir.");
        }
        return user;
    }

    public void requireCoordinator(UUID userId) {
        if (RESEARCH_ASSISTANT.equals(requireAcademician(userId).getStaffCategory())) {
            throw new ConflictException("RESEARCH_ASSISTANT_NOT_COORDINATOR",
                    "Araştırma görevlileri ders koordinatörü olamaz; derse hoca veya asistan olarak eklenebilir.");
        }
    }
}
