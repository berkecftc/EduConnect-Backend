package com.educonnect.gamificationservice.client;

import com.educonnect.common.security.ServiceTokenFeignConfiguration;
import com.educonnect.gamificationservice.client.dto.DirectoryProfile;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@FeignClient(name = "user-service", contextId = "gamificationStudentDirectory", path = "/api/users/internal/profiles",
        configuration = ServiceTokenFeignConfiguration.class)
public interface StudentDirectoryClient {

    @PostMapping("/batch")
    List<DirectoryProfile> profiles(@RequestBody Collection<UUID> userIds);
}
