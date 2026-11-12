package com.educonnect.postservice.client;

import com.educonnect.common.security.ServiceTokenFeignConfiguration;
import com.educonnect.postservice.dto.ClubAccess;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "club-service", contextId = "postClubClient", path = "/api/clubs/internal",
        configuration = ServiceTokenFeignConfiguration.class)
public interface ClubClient {

    @GetMapping("/{clubId}/access/{userId}")
    ClubAccess getAccess(@PathVariable("clubId") UUID clubId, @PathVariable("userId") UUID userId);
}
