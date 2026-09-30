package com.educonnect.clubservice;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.common.test.TestTokens;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubFinanceTest {

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

    private final UUID president = UUID.randomUUID();
    private final UUID treasurer = UUID.randomUUID();
    private final UUID auditor = UUID.randomUUID();
    private final UUID sponsorshipOfficer = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ClubMembershipRepository membershipRepository;

    @MockitoBean
    private UserClient userClient;

    private UUID clubId;

    @BeforeEach
    void seedClub() {
        Club club = new Club();
        club.setName("Mali Kulüp " + UUID.randomUUID());
        club.setAcademicAdvisorId(advisor);
        clubId = clubRepository.save(club).getId();
        seat(president, ClubPosition.PRESIDENT);
        seat(treasurer, ClubPosition.TREASURER);
        seat(auditor, ClubPosition.AUDITOR);
        seat(sponsorshipOfficer, ClubPosition.SPONSORSHIP_OFFICER);
        seat(member, ClubPosition.MEMBER);
    }

    @Test
    void theBudgetNeedsThePresidentAndTheAdvisor() throws Exception {
        String path = "/api/clubs/{clubId}/finance/budgets";
        String body = "{\"plannedIncome\":5000,\"plannedExpense\":3000,\"description\":\"Yıllık plan\"}";
        mockMvc.perform(json(post(path, clubId), TestTokens.student(member), body)).andExpect(status().isForbidden());
        String requestId = created(mockMvc.perform(json(post(path, clubId), TestTokens.student(treasurer), body))
                .andExpect(jsonPath("$.type").value("CLUB_BUDGET"))
                .andExpect(jsonPath("$.status").value("PENDING_PRESIDENT"))
                .andExpect(jsonPath("$.budget.plannedExpense").value(3000)));
        mockMvc.perform(json(post(path, clubId), TestTokens.student(treasurer), body)).andExpect(status().isConflict());

        approve(requestId, TestTokens.student(president)).andExpect(jsonPath("$.status").value("PENDING_ADVISOR"));
        approve(requestId, TestTokens.academician(advisor)).andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(as(get("/api/clubs/{clubId}/finance/summary", clubId), TestTokens.student(auditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget.plannedExpense").value(3000))
                .andExpect(jsonPath("$.academicYearLabel").isNotEmpty());
        mockMvc.perform(as(get("/api/clubs/{clubId}/finance/summary", clubId), TestTokens.student(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void smallExpensesNeedOnlyThePresidentAndLargeOnesAlsoTheAdvisor() throws Exception {
        approvedBudget(3000);
        String small = created(mockMvc.perform(entry("EXPENSE", "500", LocalDate.now(), TestTokens.student(treasurer))
                        .file(new MockMultipartFile("document", "fatura.pdf", MediaType.APPLICATION_PDF_VALUE, PDF))));
        approve(small, TestTokens.student(president)).andExpect(jsonPath("$.status").value("APPROVED"));

        String large = created(mockMvc.perform(entry("EXPENSE", "2800", LocalDate.now(), TestTokens.student(treasurer)))
                .andExpect(jsonPath("$.financeEntry.budgetExceeded").value(true)));
        approve(large, TestTokens.student(president)).andExpect(jsonPath("$.status").value("PENDING_ADVISOR"));
        approve(large, TestTokens.academician(advisor)).andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(as(get("/api/clubs/{clubId}/finance/summary", clubId), TestTokens.academician(advisor)))
                .andExpect(jsonPath("$.expense").value(3300))
                .andExpect(jsonPath("$.balance").value(-3300));
        String entries = mockMvc.perform(as(get("/api/clubs/{clubId}/finance/entries", clubId), TestTokens.student(auditor)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].status").value(hasItem("APPROVED")))
                .andReturn().getResponse().getContentAsString();
        List<String> documented = JsonPath.read(entries, "$[?(@.hasDocument == true)].id");
        String withDocument = documented.getFirst();
        byte[] downloaded = mockMvc.perform(as(get("/api/clubs/{clubId}/finance/entries/{id}/document", clubId, withDocument),
                        TestTokens.student(auditor)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(downloaded).isEqualTo(PDF);
        mockMvc.perform(as(get("/api/clubs/{clubId}/finance/entries/{id}/document", clubId, withDocument),
                        TestTokens.student(member)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidEntriesAreRejected() throws Exception {
        mockMvc.perform(entry("EXPENSE", "0", LocalDate.now(), TestTokens.student(treasurer))).andExpect(status().isBadRequest());
        mockMvc.perform(entry("INCOME", "100", LocalDate.now().plusDays(3), TestTokens.student(treasurer)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(entry("INCOME", "100", LocalDate.now(), TestTokens.student(sponsorshipOfficer)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anApprovedCashSponsorshipIsRecordedAsIncome() throws Exception {
        String path = "/api/clubs/{clubId}/finance/sponsorships";
        mockMvc.perform(as(multipart(path, clubId).param("sponsorName", "Boş Sponsor").param("description", "x")
                        .param("startsOn", LocalDate.now().toString()), TestTokens.student(sponsorshipOfficer)))
                .andExpect(status().isBadRequest());
        String requestId = created(mockMvc.perform(as(multipart(path, clubId)
                        .param("sponsorName", "Örnek Teknoloji A.Ş.")
                        .param("description", "Hackathon ana sponsorluğu")
                        .param("cashAmount", "2000")
                        .param("inKind", "Ödüller")
                        .param("startsOn", LocalDate.now().toString()), TestTokens.student(sponsorshipOfficer)))
                .andExpect(jsonPath("$.type").value("CLUB_SPONSORSHIP"))
                .andExpect(jsonPath("$.sponsorship.sponsorName").value("Örnek Teknoloji A.Ş.")));
        approve(requestId, TestTokens.student(president)).andExpect(jsonPath("$.status").value("PENDING_ADVISOR"));
        approve(requestId, TestTokens.academician(advisor)).andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(as(get("/api/clubs/{clubId}/finance/summary", clubId), TestTokens.student(treasurer)))
                .andExpect(jsonPath("$.income").value(2000));
        mockMvc.perform(as(get(path, clubId), TestTokens.student(sponsorshipOfficer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    private void approvedBudget(int plannedExpense) throws Exception {
        String requestId = created(mockMvc.perform(json(post("/api/clubs/{clubId}/finance/budgets", clubId), TestTokens.student(president),
                "{\"plannedIncome\":0,\"plannedExpense\":" + plannedExpense + "}")));
        approve(requestId, TestTokens.academician(advisor));
    }

    private MockMultipartHttpServletRequestBuilder entry(String type, String amount, LocalDate occurredOn, String token) {
        return as(multipart("/api/clubs/{clubId}/finance/entries", clubId)
                .param("type", type)
                .param("amount", amount)
                .param("description", "Etkinlik gideri")
                .param("occurredOn", occurredOn.toString()), token);
    }

    private static String created(ResultActions actions) throws Exception {
        String body = actions.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    private ResultActions approve(String requestId, String token) throws Exception {
        return mockMvc.perform(as(post("/api/clubs/{clubId}/approvals/{requestId}/approve", clubId, requestId), token))
                .andExpect(status().isOk());
    }

    private void seat(UUID studentId, ClubPosition position) {
        membershipRepository.save(new ClubMembership(clubId, studentId, position));
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
