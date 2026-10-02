package com.educonnect.postservice.service;

import com.educonnect.common.web.ApiException;
import com.educonnect.postservice.client.ClubClient;
import com.educonnect.postservice.client.CourseClient;
import com.educonnect.postservice.dto.ClubAccess;
import com.educonnect.postservice.dto.CourseAccess;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class ScopeAccessService {

    private static final Logger log = LoggerFactory.getLogger(ScopeAccessService.class);

    private final ClubClient clubClient;
    private final CourseClient courseClient;

    public ScopeAccessService(ClubClient clubClient, CourseClient courseClient) {
        this.clubClient = clubClient;
        this.courseClient = courseClient;
    }

    public ClubAccess clubAccess(UUID clubId, UUID userId) {
        try {
            return clubClient.getAccess(clubId, userId);
        } catch (FeignException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CLUB_NOT_FOUND", "Kulüp bulunamadı.");
        } catch (RuntimeException e) {
            log.error("Club access lookup failed: clubId={}, reason={}", clubId, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SCOPE_CHECK_UNAVAILABLE",
                    "Kulüp yetki bilgisi şu anda alınamıyor.");
        }
    }

    public CourseAccess courseAccess(UUID courseId, UUID userId) {
        try {
            return courseClient.getAccess(courseId, userId);
        } catch (FeignException.NotFound e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Ders bulunamadı.");
        } catch (RuntimeException e) {
            log.error("Course access lookup failed: courseId={}, reason={}", courseId, e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SCOPE_CHECK_UNAVAILABLE",
                    "Ders yetki bilgisi şu anda alınamıyor.");
        }
    }

    public boolean courseMember(UUID courseId, UUID userId) {
        try {
            CourseAccess access = courseClient.getAccess(courseId, userId);
            return access != null && access.member();
        } catch (RuntimeException e) {
            log.warn("Course membership lookup failed: courseId={}, reason={}", courseId, e.getMessage());
            return false;
        }
    }

    public Set<UUID> memberCourseIds(Viewer viewer) {
        Set<UUID> courseIds = new HashSet<>();
        if (viewer.student()) {
            courseIds.addAll(safe(() -> courseClient.getStudentCourseIds(viewer.id())));
        }
        if (viewer.academician()) {
            courseIds.addAll(safe(() -> courseClient.getStaffCourseIds(viewer.id())));
        }
        return courseIds;
    }

    private static List<UUID> safe(Supplier<List<UUID>> lookup) {
        try {
            List<UUID> ids = lookup.get();
            return ids != null ? ids : List.of();
        } catch (RuntimeException e) {
            log.warn("Course id lookup failed: reason={}", e.getMessage());
            return List.of();
        }
    }
}
