package com.educonnect.userservice.client;

import com.educonnect.common.security.ServiceTokenFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@FeignClient(name = "auth-services", contextId = "authStaffClient", path = "/api/auth/internal", configuration = ServiceTokenFeignConfiguration.class)
public interface AuthStaffClient {

    @GetMapping("/staff/{userId}/grants")
    List<StaffGrant> grants(@PathVariable("userId") UUID userId);

    record StaffGrant(String permission, UUID facultyId) {
    }
}
