package com.educonnect.assignmentservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

@Component
public class StudentAssignmentCache {

    private static final Logger log = LoggerFactory.getLogger(StudentAssignmentCache.class);

    private final CacheManager cacheManager;

    public StudentAssignmentCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evict(Collection<UUID> studentIds) {
        try {
            Cache cache = cacheManager.getCache(AssignmentService.STUDENT_ASSIGNMENTS);
            if (cache != null) {
                studentIds.forEach(cache::evict);
            }
        } catch (RuntimeException e) {
            log.warn("{} cache temizlenemedi: {}", AssignmentService.STUDENT_ASSIGNMENTS, e.getMessage());
        }
    }
}
