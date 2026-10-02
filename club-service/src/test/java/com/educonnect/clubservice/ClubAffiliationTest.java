package com.educonnect.clubservice;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.dto.message.AffiliationStatusChangedMessage;
import com.educonnect.clubservice.dto.response.UserSummary;
import com.educonnect.clubservice.listener.AffiliationStatusListener;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubDecisionLogEntry;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.model.ClubStatus;
import com.educonnect.clubservice.model.DecisionAction;
import com.educonnect.clubservice.model.MembershipEndReason;
import com.educonnect.clubservice.repository.ClubDecisionLogRepository;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.common.test.TestTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
class ClubAffiliationTest {

    private final UUID president = UUID.randomUUID();
    private final UUID vicePresident = UUID.randomUUID();
    private final UUID member = UUID.randomUUID();
    private final UUID applicant = UUID.randomUUID();
    private final UUID advisor = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ClubMembershipRepository membershipRepository;

    @Autowired
    private ClubDecisionLogRepository decisionLogRepository;

    @Autowired
    private AffiliationStatusListener listener;

    @MockitoBean
    private UserClient userClient;

    private UUID clubId;

    @BeforeEach
    void seedClub() {
        Club club = new Club();
        club.setName("Durum Kulübü " + UUID.randomUUID());
        club.setAcademicAdvisorId(advisor);
        clubId = clubRepository.save(club).getId();
        membershipRepository.save(new ClubMembership(clubId, president, ClubPosition.PRESIDENT));
        membershipRepository.save(new ClubMembership(clubId, vicePresident, ClubPosition.VICE_PRESIDENT));
        membershipRepository.save(new ClubMembership(clubId, member, ClubPosition.MEMBER));
    }

    @Test
    void aStudentOnLeaveIsFrozenOutAndReturnsAsAMember() {
        listener.onStatusChanged(message(president, "STUDENT", "ON_LEAVE", false));

        ClubMembership frozen = membership(president);
        assertThat(frozen.isActive()).isFalse();
        assertThat(frozen.getEndReason()).isEqualTo(MembershipEndReason.FROZEN);
        assertThat(frozen.getClubRole()).isEqualTo(ClubPosition.MEMBER);
        assertThat(membership(vicePresident).getClubRole()).isEqualTo(ClubPosition.PRESIDENT);
        assertThat(actions()).contains(DecisionAction.MEMBERSHIP_FROZEN, DecisionAction.VICE_PRESIDENT_PROMOTED);

        listener.onStatusChanged(message(president, "STUDENT", "ACTIVE", false));
        ClubMembership resumed = membership(president);
        assertThat(resumed.isActive()).isTrue();
        assertThat(resumed.getClubRole()).isEqualTo(ClubPosition.MEMBER);
        assertThat(resumed.getEndReason()).isNull();
        assertThat(actions()).contains(DecisionAction.MEMBERSHIP_RESUMED);
    }

    @Test
    void anEndedStudentAffiliationEndsMembershipsForGood() {
        listener.onStatusChanged(message(member, "STUDENT", "ON_LEAVE", false));
        listener.onStatusChanged(message(member, "STUDENT", "GRADUATED", true));

        assertThat(membership(member).isActive()).isFalse();
        assertThat(membership(member).getEndReason()).isEqualTo(MembershipEndReason.AFFILIATION_ENDED);
        listener.onStatusChanged(message(member, "STUDENT", "ACTIVE", false));
        assertThat(membership(member).isActive()).isFalse();
    }

    @Test
    void studentsOnLeaveCannotApplyForMembership() throws Exception {
        UserSummary onLeave = new UserSummary();
        onLeave.setId(applicant);
        onLeave.setStudentStatus("ON_LEAVE");
        when(userClient.getUserById(applicant)).thenReturn(onLeave);

        mockMvc.perform(post("/api/clubs/{clubId}/membership-requests", clubId)
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.student(applicant)))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STUDENT_ON_LEAVE"));
    }

    @Test
    void anAdvisorWhoseStaffAffiliationEndsLeavesTheClub() {
        listener.onStatusChanged(message(advisor, "STAFF", "ON_LEAVE", false));
        assertThat(clubRepository.findById(clubId).orElseThrow().getAcademicAdvisorId()).isEqualTo(advisor);

        listener.onStatusChanged(message(advisor, "STAFF", "RETIRED", true));
        Club club = clubRepository.findById(clubId).orElseThrow();
        assertThat(club.getAcademicAdvisorId()).isNull();
        assertThat(club.getStatus()).isEqualTo(ClubStatus.AWAITING_ADVISOR);
        assertThat(actions()).contains(DecisionAction.ADVISOR_LEFT);
    }

    private ClubMembership membership(UUID studentId) {
        return membershipRepository.findByClubIdAndStudentId(clubId, studentId).orElseThrow();
    }

    private List<DecisionAction> actions() {
        return decisionLogRepository.findByClubIdOrderByCreatedAtDesc(clubId).stream().map(ClubDecisionLogEntry::getAction).toList();
    }

    private static AffiliationStatusChangedMessage message(UUID userId, String affiliation, String status, boolean ended) {
        return new AffiliationStatusChangedMessage(userId, affiliation, status, ended, false, LocalDate.now(), null);
    }
}
