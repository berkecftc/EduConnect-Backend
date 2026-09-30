package com.educonnect.clubservice.controller;

import com.educonnect.clubservice.dto.request.BudgetRequest;
import com.educonnect.clubservice.dto.request.FinanceEntryForm;
import com.educonnect.clubservice.dto.request.SponsorshipForm;
import com.educonnect.clubservice.dto.response.ApprovalRequestResponse;
import com.educonnect.clubservice.dto.response.BudgetResponse;
import com.educonnect.clubservice.dto.response.FinanceEntryResponse;
import com.educonnect.clubservice.dto.response.FinanceSummaryResponse;
import com.educonnect.clubservice.dto.response.SponsorshipResponse;
import com.educonnect.clubservice.service.ClubFinanceService;
import com.educonnect.clubservice.service.ClubGovernanceService;
import com.educonnect.common.storage.SafeFileNames;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/clubs/{clubId}/finance")
@PreAuthorize("isAuthenticated()")
public class ClubFinanceController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";

    private final ClubFinanceService financeService;
    private final ClubGovernanceService governanceService;

    public ClubFinanceController(ClubFinanceService financeService, ClubGovernanceService governanceService) {
        this.financeService = financeService;
        this.governanceService = governanceService;
    }

    @GetMapping("/summary")
    public ResponseEntity<FinanceSummaryResponse> summary(@PathVariable UUID clubId,
                                                          @RequestParam(required = false) Integer academicYear,
                                                          @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(financeService.summaryOf(clubId, UUID.fromString(userIdHeader), academicYear));
    }

    @GetMapping("/budgets")
    public ResponseEntity<List<BudgetResponse>> budgets(@PathVariable UUID clubId,
                                                        @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(financeService.budgetsOf(clubId, UUID.fromString(userIdHeader)));
    }

    @PostMapping("/budgets")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApprovalRequestResponse> submitBudget(@PathVariable UUID clubId,
                                                                @Valid @RequestBody BudgetRequest request,
                                                                @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED).body(governanceService.toResponse(
                financeService.submitBudget(clubId, UUID.fromString(userIdHeader), request)));
    }

    @GetMapping("/entries")
    public ResponseEntity<List<FinanceEntryResponse>> entries(@PathVariable UUID clubId,
                                                              @RequestParam(required = false) Integer academicYear,
                                                              @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(financeService.entriesOf(clubId, UUID.fromString(userIdHeader), academicYear));
    }

    @PostMapping(value = "/entries", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApprovalRequestResponse> submitEntry(@PathVariable UUID clubId,
                                                               @Valid @ModelAttribute FinanceEntryForm form,
                                                               @RequestPart(value = "document", required = false) MultipartFile document,
                                                               @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED).body(governanceService.toResponse(
                financeService.submitEntry(clubId, UUID.fromString(userIdHeader), form, document)));
    }

    @GetMapping("/entries/{entryId}/document")
    public ResponseEntity<Resource> entryDocument(@PathVariable UUID clubId,
                                                  @PathVariable UUID entryId,
                                                  @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return download(financeService.entryDocument(clubId, entryId, UUID.fromString(userIdHeader)));
    }

    @GetMapping("/sponsorships")
    public ResponseEntity<List<SponsorshipResponse>> sponsorships(@PathVariable UUID clubId,
                                                                  @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(financeService.sponsorshipsOf(clubId, UUID.fromString(userIdHeader)));
    }

    @PostMapping(value = "/sponsorships", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ApprovalRequestResponse> submitSponsorship(@PathVariable UUID clubId,
                                                                     @Valid @ModelAttribute SponsorshipForm form,
                                                                     @RequestPart(value = "document", required = false) MultipartFile document,
                                                                     @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED).body(governanceService.toResponse(
                financeService.submitSponsorship(clubId, UUID.fromString(userIdHeader), form, document)));
    }

    @GetMapping("/sponsorships/{sponsorshipId}/document")
    public ResponseEntity<Resource> sponsorshipDocument(@PathVariable UUID clubId,
                                                        @PathVariable UUID sponsorshipId,
                                                        @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return download(financeService.sponsorshipDocument(clubId, sponsorshipId, UUID.fromString(userIdHeader)));
    }

    private static ResponseEntity<Resource> download(ClubFinanceService.Document document) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, SafeFileNames.attachmentHeader(document.fileName()))
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(document.content()));
    }
}
