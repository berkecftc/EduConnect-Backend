package com.educonnect.eventservice.client;

import com.educonnect.eventservice.dto.response.UserSummary;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * User Service ile iletişim için Feign Client.
 * Eureka üzerinden "user-service" ismiyle kayıtlı servise bağlanır.
 */
@FeignClient(name = "user-service", path = "/api/users")
public interface UserClient {

    /**
     * Kullanıcı bilgilerini ID ile getirir.
     * @param userId Kullanıcının UUID'si
     * @return Kullanıcı özet bilgileri (isim, email, bölüm)
     */
    @GetMapping("/profile/{userId}")
    UserSummary getUserById(@PathVariable("userId") UUID userId);
}

