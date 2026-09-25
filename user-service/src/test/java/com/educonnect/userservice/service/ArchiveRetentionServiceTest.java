package com.educonnect.userservice.service;

import com.educonnect.userservice.Repository.ArchivedAcademicianRepository;
import com.educonnect.userservice.Repository.ArchivedStudentRepository;
import com.educonnect.userservice.models.ArchivedStudent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArchiveRetentionServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2027-10-01T00:00:00Z"), ZoneId.of("UTC"));
    private final LocalDateTime cutoff = LocalDateTime.of(2026, 10, 1, 0, 0);

    private ArchivedStudentRepository studentRepository;
    private ArchivedAcademicianRepository academicianRepository;
    private MinioService minioService;
    private ArchiveRetentionService service;

    @BeforeEach
    void setUp() {
        studentRepository = mock(ArchivedStudentRepository.class);
        academicianRepository = mock(ArchivedAcademicianRepository.class);
        minioService = mock(MinioService.class);
        service = new ArchiveRetentionService(studentRepository, academicianRepository, minioService,
                Duration.ofDays(365), clock);
    }

    @Test
    void expiredArchivesAreDeletedWithTheirProfilePhotos() {
        ArchivedStudent expired = new ArchivedStudent(UUID.randomUUID(), "Ada", "Yılmaz", "2020001", "Bilgisayar",
                "http://localhost:9000/educonnect/profile/a.png", cutoff.minusDays(1), "test");
        when(studentRepository.findByDeletedAtBefore(cutoff)).thenReturn(List.of(expired));
        when(academicianRepository.findByDeletedAtBefore(cutoff)).thenReturn(List.of());

        assertThat(service.purgeExpired()).isEqualTo(1);

        verify(studentRepository).deleteAllInBatch(List.of(expired));
        verify(minioService).deleteFilesAfterCommit(List.of("http://localhost:9000/educonnect/profile/a.png"));
    }

    @Test
    void nothingExpiredDeletesNothing() {
        when(studentRepository.findByDeletedAtBefore(cutoff)).thenReturn(List.of());
        when(academicianRepository.findByDeletedAtBefore(cutoff)).thenReturn(List.of());

        assertThat(service.purgeExpired()).isZero();

        verify(studentRepository, never()).deleteAllInBatch(any());
        verify(minioService, never()).deleteFilesAfterCommit(any());
    }
}
