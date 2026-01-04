package com.educonnect.clubservice.client;

import com.educonnect.clubservice.dto.response.AcademicianSummary;
import com.educonnect.clubservice.dto.response.UserSummary; // Birazdan oluşturacağız
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

// "user-service" -> Eureka'da kayıtlı olan servis adı (birebir aynı olmalı)
@FeignClient(name = "user-service", path = "/api/users")
public interface UserClient {

    // User Service'teki metodun aynısı: @GetMapping("/profile/{userId}")
    @GetMapping("/profile/{userId}")
    UserSummary getUserById(@PathVariable("userId") UUID userId);

    // Akademisyen bilgisi al (danışman hoca için)
    @GetMapping("/profile/{userId}")
    AcademicianSummary getAcademicianById(@PathVariable("userId") UUID userId);
}