package com.educonnect.courseservice.client;

import com.educonnect.courseservice.dto.UserSummaryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.UUID;

@FeignClient(name = "user-service", path = "/api/users")
public interface UserClient {
    @GetMapping("/profile/{userId}")
    UserSummaryDto getUserById(@PathVariable("userId") UUID userId);
}