package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ClubElection;
import com.educonnect.clubservice.model.ClubElectionCandidate;
import com.educonnect.clubservice.model.ElectionBallot;
import com.educonnect.clubservice.model.ElectionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ElectionResponse(UUID id,
                               UUID clubId,
                               ElectionStatus status,
                               int boardSeats,
                               int auditSeats,
                               String note,
                               UUID openedBy,
                               Instant openedAt,
                               Instant votingStartedAt,
                               Instant closedAt,
                               Integer eligibleVoters,
                               Integer voters,
                               UUID requestId,
                               Instant completedAt,
                               List<Candidate> candidates,
                               Set<ElectionBallot> myBallots) {

    public record Candidate(UUID id,
                            ElectionBallot ballot,
                            UUID studentId,
                            String firstName,
                            String lastName,
                            Integer votes,
                            boolean elected) {
    }

    public static ElectionResponse of(ClubElection election, List<ClubElectionCandidate> candidates,
                                      Map<UUID, UserSummary> users, Set<ElectionBallot> myBallots) {
        boolean counted = election.getClosedAt() != null;
        return new ElectionResponse(election.getId(), election.getClubId(), election.getStatus(), election.getBoardSeats(),
                election.getAuditSeats(), election.getNote(), election.getOpenedBy(), election.getOpenedAt(),
                election.getVotingStartedAt(), election.getClosedAt(), election.getEligibleVoters(), election.getVoters(),
                election.getRequestId(), election.getCompletedAt(),
                candidates.stream()
                        .map(candidate -> {
                            UserSummary user = users.get(candidate.getStudentId());
                            return new Candidate(candidate.getId(), candidate.getBallot(), candidate.getStudentId(),
                                    user != null ? user.getFirstName() : null, user != null ? user.getLastName() : null,
                                    counted ? candidate.getVotes() : null, counted && candidate.isElected());
                        })
                        .toList(),
                myBallots);
    }
}
