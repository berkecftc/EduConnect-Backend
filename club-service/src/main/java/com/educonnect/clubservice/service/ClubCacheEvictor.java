package com.educonnect.clubservice.service;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ClubCacheEvictor {

    private final CacheManager cacheManager;

    public ClubCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictUser(UUID userId) {
        if (userId == null) {
            return;
        }
        evict("managedClubs", userId);
        evict("studentClubMemberships", userId);
    }

    private void evict(String cacheName, UUID key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }
}
