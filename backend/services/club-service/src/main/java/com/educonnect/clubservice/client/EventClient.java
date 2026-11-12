package com.educonnect.clubservice.client;

import com.educonnect.clubservice.dto.response.ClubEventStatistics;
import com.educonnect.common.security.ServiceTokenFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(name = "event-service", path = "/api/events/internal", configuration = ServiceTokenFeignConfiguration.class)
public interface EventClient {

    @GetMapping("/clubs/{clubId}/statistics")
    ClubEventStatistics clubStatistics(@PathVariable("clubId") UUID clubId,
                                       @RequestParam("from") String from,
                                       @RequestParam("to") String to);
}
