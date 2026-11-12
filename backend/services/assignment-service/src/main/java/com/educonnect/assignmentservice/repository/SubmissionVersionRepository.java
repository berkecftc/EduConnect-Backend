package com.educonnect.assignmentservice.repository;

import com.educonnect.assignmentservice.model.SubmissionVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubmissionVersionRepository extends JpaRepository<SubmissionVersion, UUID> {
    List<SubmissionVersion> findBySubmissionIdOrderByVersionNoDesc(UUID submissionId);
    List<SubmissionVersion> findBySubmissionIdIn(Collection<UUID> submissionIds);
    int countBySubmissionId(UUID submissionId);
    Optional<SubmissionVersion> findFirstByFileUrl(String fileUrl);
}
