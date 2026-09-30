package com.educonnect.clubservice.service;

import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.security.ClubAccess;

import java.util.UUID;

interface ApprovalHandler {

    ApprovalType type();

    default boolean needsPresidentApproval(ClubApprovalRequest request, ClubAccess preparer) {
        return false;
    }

    default UUID advisorStageDecider(Club club, ClubApprovalRequest request) {
        return club.getAcademicAdvisorId();
    }

    void onAwaitingDecision(Club club, ClubApprovalRequest request, UUID deciderId);

    void apply(Club club, ClubApprovalRequest request, UUID approverId);

    void onRejected(Club club, ClubApprovalRequest request);

    default void onWithdrawn(Club club, ClubApprovalRequest request) {
    }
}
