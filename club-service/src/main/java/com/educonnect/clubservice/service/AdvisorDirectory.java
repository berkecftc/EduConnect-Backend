package com.educonnect.clubservice.service;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.dto.response.AcademicianSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class AdvisorDirectory {

    private static final Logger log = LoggerFactory.getLogger(AdvisorDirectory.class);

    private final UserClient userClient;

    public AdvisorDirectory(UserClient userClient) {
        this.userClient = userClient;
    }

    public void requireAcademician(UUID advisorId) {
        if (advisorId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kulübün bir danışman akademisyeni olmalıdır.");
        }
        AcademicianSummary advisor;
        try {
            advisor = userClient.getAcademicianById(advisorId);
        } catch (Exception e) {
            log.warn("Advisor lookup failed for {}: {}", advisorId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danışman akademisyen bulunamadı.");
        }
        if (advisor == null || !"Academician".equalsIgnoreCase(advisor.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danışman olarak yalnızca bir akademisyen seçilebilir.");
        }
    }
}
