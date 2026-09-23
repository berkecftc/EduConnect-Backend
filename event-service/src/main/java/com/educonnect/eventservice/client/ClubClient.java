package com.educonnect.eventservice.client;

import com.educonnect.common.security.ServiceTokenFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/**
 * Club Service ile iletişim için Feign Client.
 * Eureka üzerinden "club-service" ismiyle kayıtlı servise bağlanır.
 * Etkinlik onayı için kulübün danışman akademisyen bilgisini almak amacıyla kullanılır.
 */
@FeignClient(name = "club-service", path = "/api/clubs/internal", configuration = ServiceTokenFeignConfiguration.class)
public interface ClubClient {

    /**
     * Bir kulübün danışman akademisyen ID'sini getirir.
     * @param clubId Kulübün UUID'si
     * @return Danışman akademisyen ID'si
     */
    @GetMapping("/{clubId}/advisor-id")
    UUID getClubAdvisorId(@PathVariable("clubId") UUID clubId);

    /**
     * Bir danışman akademisyenin sorumlu olduğu kulüplerin ID listesini getirir.
     * @param advisorId Danışman akademisyen ID'si
     * @return Kulüp ID listesi
     */
    @GetMapping("/by-advisor/{advisorId}/ids")
    List<UUID> getClubIdsByAdvisorId(@PathVariable("advisorId") UUID advisorId);

    @GetMapping("/{clubId}/is-member/{studentId}")
    Boolean isStudentMemberOfClub(@PathVariable("clubId") UUID clubId, @PathVariable("studentId") UUID studentId);
}
