package com.educonnect.userservice.client;

import com.educonnect.common.security.ServiceTokenFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;
import java.util.UUID;

@FeignClient(name = "club-service", contextId = "clubRelationClient", path = "/api/clubs/internal", configuration = ServiceTokenFeignConfiguration.class)
public interface ClubRelationClient {

    @GetMapping("/managers/{viewerId}/students/{studentId}")
    Map<String, Boolean> managesStudent(@PathVariable("viewerId") UUID viewerId, @PathVariable("studentId") UUID studentId);
}
