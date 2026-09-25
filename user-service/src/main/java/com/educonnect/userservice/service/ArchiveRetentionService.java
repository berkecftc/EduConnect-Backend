package com.educonnect.userservice.service;

import com.educonnect.userservice.Repository.ArchivedAcademicianRepository;
import com.educonnect.userservice.Repository.ArchivedStudentRepository;
import com.educonnect.userservice.models.ArchivedAcademician;
import com.educonnect.userservice.models.ArchivedStudent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ArchiveRetentionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ArchiveRetentionService.class);

    private final ArchivedStudentRepository archivedStudentRepository;
    private final ArchivedAcademicianRepository archivedAcademicianRepository;
    private final MinioService minioService;
    private final Duration retention;
    private final Clock clock;

    @Autowired
    public ArchiveRetentionService(ArchivedStudentRepository archivedStudentRepository,
                                   ArchivedAcademicianRepository archivedAcademicianRepository,
                                   MinioService minioService,
                                   @Value("${educonnect.user.archive.retention:365d}") Duration retention) {
        this(archivedStudentRepository, archivedAcademicianRepository, minioService, retention, Clock.systemDefaultZone());
    }

    ArchiveRetentionService(ArchivedStudentRepository archivedStudentRepository,
                            ArchivedAcademicianRepository archivedAcademicianRepository,
                            MinioService minioService,
                            Duration retention,
                            Clock clock) {
        this.archivedStudentRepository = archivedStudentRepository;
        this.archivedAcademicianRepository = archivedAcademicianRepository;
        this.minioService = minioService;
        this.retention = retention;
        this.clock = clock;
    }

    @Scheduled(cron = "${educonnect.user.archive.purge-cron:0 15 3 * * *}")
    @Transactional
    public int purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(retention);
        List<ArchivedStudent> students = archivedStudentRepository.findByDeletedAtBefore(cutoff);
        List<ArchivedAcademician> academicians = archivedAcademicianRepository.findByDeletedAtBefore(cutoff);
        if (students.isEmpty() && academicians.isEmpty()) {
            return 0;
        }
        List<String> files = new ArrayList<>();
        students.forEach(student -> files.add(student.getProfileImageUrl()));
        academicians.forEach(academician -> files.add(academician.getProfileImageUrl()));
        archivedStudentRepository.deleteAllInBatch(students);
        archivedAcademicianRepository.deleteAllInBatch(academicians);
        minioService.deleteFilesAfterCommit(files);
        int purged = students.size() + academicians.size();
        LOGGER.info("Saklama süresi dolan {} arşiv kaydı silindi (sınır: {})", purged, cutoff);
        return purged;
    }
}
