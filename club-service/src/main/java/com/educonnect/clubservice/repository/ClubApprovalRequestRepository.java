package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubPosition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ClubApprovalRequestRepository extends JpaRepository<ClubApprovalRequest, UUID> {

    List<ClubApprovalRequest> findByClubIdOrderByCreatedAtDesc(UUID clubId);

    List<ClubApprovalRequest> findByClubIdAndTypeOrderByCreatedAtDesc(UUID clubId, ApprovalType type);

    List<ClubApprovalRequest> findByClubIdAndStatusIn(UUID clubId, Collection<ApprovalStatus> statuses);

    List<ClubApprovalRequest> findByClubIdInAndStatusOrderByCreatedAtAsc(Collection<UUID> clubIds, ApprovalStatus status);

    List<ClubApprovalRequest> findByClubIdInAndTypeAndStatus(Collection<UUID> clubIds, ApprovalType type, ApprovalStatus status);

    List<ClubApprovalRequest> findByTypeAndSubjectUserIdAndStatus(ApprovalType type, UUID subjectUserId, ApprovalStatus status);

    boolean existsByClubIdAndTypeAndStatusIn(UUID clubId, ApprovalType type, Collection<ApprovalStatus> statuses);

    boolean existsByClubIdAndTypeAndSubjectUserIdAndStatusIn(UUID clubId, ApprovalType type, UUID subjectUserId,
                                                            Collection<ApprovalStatus> statuses);

    long countByClubIdAndTypeAndStatusIn(UUID clubId, ApprovalType type, Collection<ApprovalStatus> statuses);

    long countByClubIdAndTypeAndRequestedPositionAndStatusIn(UUID clubId, ApprovalType type, ClubPosition position,
                                                            Collection<ApprovalStatus> statuses);
}
