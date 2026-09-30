package com.educonnect.clubservice.service;

import com.educonnect.clubservice.dto.response.AnnouncementResponse;
import com.educonnect.clubservice.dto.response.ApprovalDetails;
import com.educonnect.clubservice.dto.response.BudgetResponse;
import com.educonnect.clubservice.dto.response.FinanceEntryResponse;
import com.educonnect.clubservice.dto.response.MeetingResponse;
import com.educonnect.clubservice.dto.response.ProfileChangeResponse;
import com.educonnect.clubservice.dto.response.SponsorshipResponse;
import com.educonnect.clubservice.model.ApprovalType;
import com.educonnect.clubservice.model.ClubApprovalRequest;
import com.educonnect.clubservice.model.ClubMeeting;
import com.educonnect.clubservice.model.ClubMeetingDecision;
import com.educonnect.clubservice.repository.ClubAnnouncementRepository;
import com.educonnect.clubservice.repository.ClubBudgetRepository;
import com.educonnect.clubservice.repository.ClubFinanceEntryRepository;
import com.educonnect.clubservice.repository.ClubMeetingDecisionRepository;
import com.educonnect.clubservice.repository.ClubMeetingRepository;
import com.educonnect.clubservice.repository.ClubProfileChangeRepository;
import com.educonnect.clubservice.repository.ClubSponsorshipRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Transactional(readOnly = true)
public class ApprovalDetailsLoader {

    private static final Set<ApprovalType> PROFILE_TYPES = Set.of(ApprovalType.CLUB_PROFILE_UPDATE, ApprovalType.CLUB_LOGO_CHANGE);

    private final ClubProfileChangeRepository profileChangeRepository;
    private final ClubAnnouncementRepository announcementRepository;
    private final ClubBudgetRepository budgetRepository;
    private final ClubFinanceEntryRepository entryRepository;
    private final ClubSponsorshipRepository sponsorshipRepository;
    private final ClubMeetingRepository meetingRepository;
    private final ClubMeetingDecisionRepository decisionRepository;

    public ApprovalDetailsLoader(ClubProfileChangeRepository profileChangeRepository,
                                 ClubAnnouncementRepository announcementRepository,
                                 ClubBudgetRepository budgetRepository,
                                 ClubFinanceEntryRepository entryRepository,
                                 ClubSponsorshipRepository sponsorshipRepository,
                                 ClubMeetingRepository meetingRepository,
                                 ClubMeetingDecisionRepository decisionRepository) {
        this.profileChangeRepository = profileChangeRepository;
        this.announcementRepository = announcementRepository;
        this.budgetRepository = budgetRepository;
        this.entryRepository = entryRepository;
        this.sponsorshipRepository = sponsorshipRepository;
        this.meetingRepository = meetingRepository;
        this.decisionRepository = decisionRepository;
    }

    public Map<UUID, ApprovalDetails> detailsOf(List<ClubApprovalRequest> requests) {
        Map<UUID, ClubApprovalRequest> byId = requests.stream()
                .collect(Collectors.toMap(ClubApprovalRequest::getId, Function.identity(), (first, second) -> first));
        Map<UUID, ApprovalDetails> details = new HashMap<>();
        profileChangeRepository.findAllById(idsOf(requests, PROFILE_TYPES))
                .forEach(change -> details.put(change.getRequestId(),
                        ApprovalDetails.ofProfileChange(ProfileChangeResponse.of(change))));
        announcementRepository.findByRequestIdIn(idsOf(requests, Set.of(ApprovalType.CLUB_ANNOUNCEMENT)))
                .forEach(announcement -> details.put(announcement.getRequestId(),
                        ApprovalDetails.ofAnnouncement(AnnouncementResponse.of(announcement))));
        budgetRepository.findByRequestIdIn(idsOf(requests, Set.of(ApprovalType.CLUB_BUDGET)))
                .forEach(budget -> details.put(budget.getRequestId(), ApprovalDetails.ofBudget(
                        BudgetResponse.of(budget, byId.get(budget.getRequestId()).getStatus()))));
        entryRepository.findByRequestIdIn(idsOf(requests, Set.of(ApprovalType.CLUB_FINANCE_ENTRY)))
                .forEach(entry -> details.put(entry.getRequestId(), ApprovalDetails.ofFinanceEntry(
                        FinanceEntryResponse.of(entry, byId.get(entry.getRequestId()).getStatus()))));
        sponsorshipRepository.findByRequestIdIn(idsOf(requests, Set.of(ApprovalType.CLUB_SPONSORSHIP)))
                .forEach(sponsorship -> details.put(sponsorship.getRequestId(), ApprovalDetails.ofSponsorship(
                        SponsorshipResponse.of(sponsorship, byId.get(sponsorship.getRequestId()).getStatus()))));
        List<ClubMeeting> meetings = meetingRepository.findByRequestIdIn(idsOf(requests, Set.of(ApprovalType.CLUB_MEETING_MINUTES)));
        Map<UUID, List<ClubMeetingDecision>> decisions = decisionRepository
                .findByMeetingIdInOrderByItemOrder(meetings.stream().map(ClubMeeting::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(ClubMeetingDecision::getMeetingId));
        meetings.forEach(meeting -> details.put(meeting.getRequestId(), ApprovalDetails.ofMeeting(MeetingResponse.of(meeting,
                decisions.getOrDefault(meeting.getId(), List.of()), byId.get(meeting.getRequestId()).getStatus()))));
        return details;
    }

    private static List<UUID> idsOf(List<ClubApprovalRequest> requests, Set<ApprovalType> types) {
        return requests.stream()
                .filter(request -> types.contains(request.getType()))
                .map(ClubApprovalRequest::getId)
                .toList();
    }
}
