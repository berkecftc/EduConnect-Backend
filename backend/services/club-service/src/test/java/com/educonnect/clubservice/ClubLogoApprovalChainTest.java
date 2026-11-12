package com.educonnect.clubservice;

import com.educonnect.clubservice.client.UserClient;
import com.educonnect.clubservice.model.Club;
import com.educonnect.clubservice.model.ClubMembership;
import com.educonnect.clubservice.model.ClubPosition;
import com.educonnect.clubservice.repository.ClubMembershipRepository;
import com.educonnect.clubservice.repository.ClubRepository;
import com.educonnect.common.test.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ClubIntegrationTest
@TestPropertySource(properties = "educonnect.club.approval-chain.enabled=true")
class ClubLogoApprovalChainTest {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClubRepository clubRepository;

    @Autowired
    private ClubMembershipRepository membershipRepository;

    @MockitoBean
    private UserClient userClient;

    @Test
    void withTheApprovalChainOnTheDirectLogoUploadIsClosed() throws Exception {
        UUID president = UUID.randomUUID();
        Club club = new Club();
        club.setName("Zincir Kulübü " + UUID.randomUUID());
        club.setAcademicAdvisorId(UUID.randomUUID());
        UUID clubId = clubRepository.save(club).getId();
        membershipRepository.save(new ClubMembership(clubId, president, ClubPosition.PRESIDENT));

        mockMvc.perform(multipart("/api/clubs/{clubId}/logo", clubId)
                        .file(new MockMultipartFile("file", "logo.png", MediaType.IMAGE_PNG_VALUE, PNG))
                        .header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(TestTokens.student(president))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("APPROVAL_REQUIRED"));
    }
}
