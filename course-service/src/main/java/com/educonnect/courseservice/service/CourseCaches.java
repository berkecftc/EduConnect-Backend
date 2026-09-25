package com.educonnect.courseservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

@Component
public class CourseCaches {

    public static final String STUDENT_COURSES = "studentCourses";
    public static final String INSTRUCTOR_COURSES = "instructorCourses";

    private static final Logger log = LoggerFactory.getLogger(CourseCaches.class);

    private final CacheManager cacheManager;

    public CourseCaches(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictInstructorCourses(UUID instructorId) {
        evict(INSTRUCTOR_COURSES, instructorId);
    }

    public void evictStudentCourses(UUID studentId) {
        evict(STUDENT_COURSES, studentId);
    }

    public void evictStudentCourses(Collection<UUID> studentIds) {
        studentIds.forEach(this::evictStudentCourses);
    }

    private void evict(String cacheName, UUID key) {
        if (key == null) {
            return;
        }
        try {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (RuntimeException e) {
            log.warn("{} cache temizlenemedi: {}", cacheName, e.getMessage());
        }
    }
}
