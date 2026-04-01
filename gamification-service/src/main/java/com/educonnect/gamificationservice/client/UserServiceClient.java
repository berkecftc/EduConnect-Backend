package com.educonnect.gamificationservice.client;

import com.educonnect.gamificationservice.client.dto.UserProfileClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service", path = "/api/users")
public interface UserServiceClient {

    @GetMapping("/profile/{userId}")
    UserProfileClientResponse getProfileById(@PathVariable("userId") UUID userId);
}

