package com.educonnect.llmservice.client;

import com.educonnect.common.security.ServiceTokenFeignConfiguration;
import com.educonnect.llmservice.dto.ClubCatalogItem;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "club-service", contextId = "clubCatalogClient", path = "/api/clubs/internal", configuration = ServiceTokenFeignConfiguration.class)
public interface ClubServiceClient {

    @GetMapping("/catalog")
    List<ClubCatalogItem> getClubCatalog();
}
