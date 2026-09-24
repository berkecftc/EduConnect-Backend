package com.educonnect.clubservice.client;

import com.educonnect.clubservice.dto.response.AcademicianSummary;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.common.security.ServiceTokenFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "user-service", path = "/api/users/internal/profiles", configuration = ServiceTokenFeignConfiguration.class)
public interface UserClient {

    @GetMapping("/{userId}")
    UserSummary getUserById(@PathVariable("userId") UUID userId);

    @GetMapping("/{userId}")
    AcademicianSummary getAcademicianById(@PathVariable("userId") UUID userId);

    @GetMapping("/by-student-number/{studentNumber}")
    UserSummary getUserByStudentNumber(@PathVariable("studentNumber") String studentNumber);
}
