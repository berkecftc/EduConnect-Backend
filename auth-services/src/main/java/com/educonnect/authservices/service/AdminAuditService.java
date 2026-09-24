package com.educonnect.authservices.service;

import com.educonnect.authservices.Repository.AdminAuditRepository;
import com.educonnect.authservices.dto.response.AdminAuditPage;
import com.educonnect.authservices.models.AdminAuditEntry;
import com.educonnect.authservices.models.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AdminAuditService {

    static final int MAX_DETAILS_LENGTH = 1000;
    static final int MAX_PAGE_SIZE = 100;

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private final AdminAuditRepository repository;
    private final Clock clock;

    @Autowired
    public AdminAuditService(AdminAuditRepository repository) {
        this(repository, Clock.systemUTC());
    }

    AdminAuditService(AdminAuditRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void record(String action, String targetType, Object targetId, String details) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID actorId = null;
        String actorEmail = null;
        if (authentication != null) {
            if (authentication.getPrincipal() instanceof User user) {
                actorId = user.getId();
                actorEmail = user.getEmail();
            } else {
                actorEmail = authentication.getName();
            }
        }
        String safeDetails = details == null ? null
                : details.length() > MAX_DETAILS_LENGTH ? details.substring(0, MAX_DETAILS_LENGTH) : details;
        String target = String.valueOf(targetId);
        repository.save(new AdminAuditEntry(actorId, actorEmail, action, targetType, target, safeDetails, clock.instant()));
        AUDIT.info("action={} targetType={} targetId={} actorId={}", action, targetType, target, actorId);
    }

    @Transactional(readOnly = true)
    public AdminAuditPage list(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<AdminAuditEntry> result = repository.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage, safeSize));
        return new AdminAuditPage(
                result.getContent().stream().map(AdminAuditPage.Entry::from).toList(),
                safePage, safeSize, result.getTotalElements());
    }
}
