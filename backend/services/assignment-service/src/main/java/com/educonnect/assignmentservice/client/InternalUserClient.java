package com.educonnect.assignmentservice.client;

import com.educonnect.common.security.ServiceTokenFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service", contextId = "internalUserClient", path = "/api/users/internal/profiles",
        configuration = ServiceTokenFeignConfiguration.class)
public interface InternalUserClient {

    @PostMapping("/batch")
    List<UserClient.UserProfileDTO> getUsersByIds(@RequestBody Collection<UUID> userIds);
}
