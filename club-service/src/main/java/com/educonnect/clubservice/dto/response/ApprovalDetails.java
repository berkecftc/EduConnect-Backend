package com.educonnect.clubservice.dto.response;

public record ApprovalDetails(ProfileChangeResponse profileChange,
                              AnnouncementResponse announcement,
                              BudgetResponse budget,
                              FinanceEntryResponse financeEntry,
                              SponsorshipResponse sponsorship,
                              MeetingResponse meeting,
                              ReportResponse report) {

    public static final ApprovalDetails NONE = new ApprovalDetails(null, null, null, null, null, null, null);

    public static ApprovalDetails ofProfileChange(ProfileChangeResponse profileChange) {
        return new ApprovalDetails(profileChange, null, null, null, null, null, null);
    }

    public static ApprovalDetails ofAnnouncement(AnnouncementResponse announcement) {
        return new ApprovalDetails(null, announcement, null, null, null, null, null);
    }

    public static ApprovalDetails ofBudget(BudgetResponse budget) {
        return new ApprovalDetails(null, null, budget, null, null, null, null);
    }

    public static ApprovalDetails ofFinanceEntry(FinanceEntryResponse financeEntry) {
        return new ApprovalDetails(null, null, null, financeEntry, null, null, null);
    }

    public static ApprovalDetails ofSponsorship(SponsorshipResponse sponsorship) {
        return new ApprovalDetails(null, null, null, null, sponsorship, null, null);
    }

    public static ApprovalDetails ofMeeting(MeetingResponse meeting) {
        return new ApprovalDetails(null, null, null, null, null, meeting, null);
    }

    public static ApprovalDetails ofReport(ReportResponse report) {
        return new ApprovalDetails(null, null, null, null, null, null, report);
    }
}
