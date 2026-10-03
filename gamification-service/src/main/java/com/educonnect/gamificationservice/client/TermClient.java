package com.educonnect.gamificationservice.client;

import com.educonnect.common.security.ServiceTokenFeignConfiguration;
import com.educonnect.gamificationservice.client.dto.TermWindow;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "course-service", contextId = "gamificationTerms", path = "/api/courses/internal",
        configuration = ServiceTokenFeignConfiguration.class)
public interface TermClient {

    @GetMapping("/terms/current")
    TermWindow currentTerm();
}
