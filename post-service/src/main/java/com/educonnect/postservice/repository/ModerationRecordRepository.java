package com.educonnect.postservice.repository;

import com.educonnect.postservice.model.ModerationRecord;
import com.educonnect.postservice.model.ModerationTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ModerationRecordRepository extends JpaRepository<ModerationRecord, UUID> {

    List<ModerationRecord> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(ModerationTarget targetType, UUID targetId);
}
