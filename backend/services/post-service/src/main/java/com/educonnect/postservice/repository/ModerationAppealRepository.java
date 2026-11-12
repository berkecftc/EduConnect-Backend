package com.educonnect.postservice.repository;

import com.educonnect.postservice.model.ModerationAppeal;
import com.educonnect.postservice.model.ModerationTarget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModerationAppealRepository extends JpaRepository<ModerationAppeal, UUID> {

    boolean existsByTargetTypeAndTargetId(ModerationTarget targetType, UUID targetId);

    Page<ModerationAppeal> findByStatus(ModerationAppeal.Status status, Pageable pageable);

    List<ModerationAppeal> findByAppellantIdOrderByCreatedAtDesc(UUID appellantId);
}
