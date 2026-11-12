package com.educonnect.clubservice.dto.response;

import com.educonnect.clubservice.model.ApprovalStatus;
import com.educonnect.clubservice.model.ClubSponsorship;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SponsorshipResponse(UUID id,
                                  String sponsorName,
                                  String description,
                                  BigDecimal cashAmount,
                                  String inKind,
                                  LocalDate startsOn,
                                  LocalDate endsOn,
                                  boolean hasDocument,
                                  String documentName,
                                  UUID preparedBy,
                                  Instant createdAt,
                                  Instant approvedAt,
                                  ApprovalStatus status) {

    public static SponsorshipResponse of(ClubSponsorship sponsorship, ApprovalStatus status) {
        if (sponsorship == null) {
            return null;
        }
        return new SponsorshipResponse(sponsorship.getId(), sponsorship.getSponsorName(), sponsorship.getDescription(),
                sponsorship.getCashAmount(), sponsorship.getInKind(), sponsorship.getStartsOn(), sponsorship.getEndsOn(),
                sponsorship.getDocumentObject() != null, sponsorship.getDocumentName(), sponsorship.getPreparedBy(),
                sponsorship.getCreatedAt(), sponsorship.getApprovedAt(), status);
    }
}
