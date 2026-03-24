package com.educonnect.gamificationservice.scheduler;

import com.educonnect.gamificationservice.service.GamificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class GamificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(GamificationScheduler.class);

    private final GamificationService gamificationService;

    public GamificationScheduler(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Europe/Istanbul")
    public void resetInactiveStreaks() {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Europe/Istanbul")).minusDays(1);
        int resetCount = gamificationService.resetInactiveStreaks(yesterday);
        if (resetCount > 0) {
            log.info("Inactive streak reset completed. resetCount={}", resetCount);
        }
    }
}

