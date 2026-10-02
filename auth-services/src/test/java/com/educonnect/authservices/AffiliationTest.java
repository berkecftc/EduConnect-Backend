package com.educonnect.authservices;

import com.educonnect.authservices.models.AcademicianRegistrationRequest;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.StudentRegistrationRequest;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.AcademicianRequestRepository;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.authservices.service.InstitutionPolicy;
import com.educonnect.authservices.service.JWTService;
import com.educonnect.authservices.service.ProfileNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AuthIntegrationTest
class AffiliationTest {

    private static final String PASSWORD = "Pw-" + UUID.randomUUID();
    private static final byte[] PDF = "%PDF-1.4 belge".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRequestRepository studentRequestRepository;

    @Autowired
    private AcademicianRequestRepository academicianRequestRepository;

    @Autowired
    private JWTService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private InstitutionPolicy institutionPolicy;

    @MockitoBean
    private ProfileNames profileNames;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = save(Role.ROLE_ADMIN);
        when(institutionPolicy.requireStudentNumber(anyString())).thenAnswer(call -> call.getArgument(0));
        when(profileNames.of(any())).thenReturn(new ProfileNames.Names("Deniz", "Arslan"));
    }

    @Test
    void anAcademicianAddsAStudentAffiliationToTheSameAccount() throws Exception {
        User academician = save(Role.ROLE_ACADEMICIAN);
        String number = "9" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 100000000L);

        mockMvc.perform(studentAffiliation(number)).andExpect(status().isUnauthorized());
        mockMvc.perform(as(studentAffiliation(number), academician)).andExpect(status().isAccepted());
        mockMvc.perform(as(studentAffiliation(number), academician))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AFFILIATION_PENDING"));

        StudentRegistrationRequest request = studentRequestRepository.findByUserId(academician.getId()).orElseThrow();
        assertThat(request.getFirstName()).isEqualTo("Deniz");
        assertThat(request.getEmail()).isEqualTo(academician.getEmail());
        assertThat(roles(academician)).containsExactlyInAnyOrder(Role.ROLE_ACADEMICIAN, Role.ROLE_PENDING_STUDENT);
        login(academician).andExpect(status().isOk()).andExpect(jsonPath("$.pendingRequests[0]").value("STUDENT"));

        mockMvc.perform(as(post("/api/auth/admin/approve-student/{id}", request.getId()), admin)).andExpect(status().isOk());
        User approved = userRepository.findById(academician.getId()).orElseThrow();
        assertThat(approved.getRoles()).containsExactlyInAnyOrder(Role.ROLE_ACADEMICIAN, Role.ROLE_STUDENT);
        assertThat(approved.getStudentNumber()).isEqualTo(number);
        assertThat(studentRequestRepository.findByUserId(academician.getId())).isEmpty();
        assertThat(userRepository.findAll().stream().filter(u -> u.getEmail().equals(academician.getEmail()))).hasSize(1);
        login(academician).andExpect(status().isOk()).andExpect(jsonPath("$.primaryRole").value("ROLE_ACADEMICIAN"));

        mockMvc.perform(as(studentAffiliation(number), academician))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AFFILIATION_EXISTS"));
    }

    @Test
    void aStudentAppliesForAStaffAffiliationAndKeepsTheAccountWhenRejected() throws Exception {
        User student = save(Role.ROLE_STUDENT);

        mockMvc.perform(as(studentAffiliation("12345"), student))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AFFILIATION_NOT_ALLOWED"));
        mockMvc.perform(as(staffAffiliation("Profesör"), student))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TITLE"));
        mockMvc.perform(as(staffAffiliation("arş. gör."), student)).andExpect(status().isAccepted());

        AcademicianRegistrationRequest request = academicianRequestRepository.findByUserId(student.getId()).orElseThrow();
        assertThat(request.getTitle()).isEqualTo("Arş. Gör.");
        assertThat(request.getLastName()).isEqualTo("Arslan");
        assertThat(roles(student)).containsExactlyInAnyOrder(Role.ROLE_STUDENT, Role.ROLE_PENDING_ACADEMICIAN);
        login(student).andExpect(status().isOk());
        mockMvc.perform(as(staffAffiliation("Arş. Gör."), student))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("AFFILIATION_PENDING"));

        mockMvc.perform(as(post("/api/auth/admin/reject-academician/{id}", student.getId()), admin)).andExpect(status().isOk());
        assertThat(roles(student)).containsExactly(Role.ROLE_STUDENT);
        assertThat(academicianRequestRepository.findByUserId(student.getId())).isEmpty();

        mockMvc.perform(as(staffAffiliation("Dr. Öğr. Üyesi"), student)).andExpect(status().isAccepted());
        mockMvc.perform(as(post("/api/auth/admin/approve-academician/{id}", student.getId()), admin)).andExpect(status().isOk());
        assertThat(roles(student)).containsExactlyInAnyOrder(Role.ROLE_STUDENT, Role.ROLE_ACADEMICIAN);
    }

    @Test
    void newAcademicianApplicationsNeedACatalogTitle() throws Exception {
        String email = "hoca-" + UUID.randomUUID() + "@test.educonnect.local";
        MockMultipartFile request = new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                ("{\"email\":\"" + email + "\",\"password\":\"Guclu-Sifre-2026!\",\"firstName\":\"Ali\",\"lastName\":\"Can\",\"title\":\"Hoca\"}")
                        .getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/auth/request/academician-account").file(request)
                        .file(new MockMultipartFile("idCardImage", "kart.png", MediaType.IMAGE_PNG_VALUE, PNG)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TITLE"));
        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    private MockMultipartHttpServletRequestBuilder studentAffiliation(String number) {
        return multipart("/api/auth/affiliations/student")
                .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                        ("{\"studentNumber\":\"" + number + "\",\"entryYear\":2025}").getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("studentDocument", "belge.pdf", MediaType.APPLICATION_PDF_VALUE, PDF));
    }

    private MockMultipartHttpServletRequestBuilder staffAffiliation(String title) {
        return multipart("/api/auth/affiliations/academician")
                .file(new MockMultipartFile("request", "", MediaType.APPLICATION_JSON_VALUE,
                        ("{\"title\":\"" + title + "\",\"officeNumber\":\"B-1\"}").getBytes(StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("idCardImage", "kart.png", MediaType.IMAGE_PNG_VALUE, PNG));
    }

    private ResultActions login(User user) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}"));
    }

    private Set<Role> roles(User user) {
        return Set.copyOf(userRepository.findById(user.getId()).orElseThrow().getRoles());
    }

    private <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, User user) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(user));
    }

    private User save(Role role) {
        User user = new User(role.name().toLowerCase().replace("role_", "") + "-" + UUID.randomUUID() + "@test.educonnect.local",
                passwordEncoder.encode(PASSWORD), Set.of(role));
        user.setEmailVerifiedAt(Instant.now());
        return userRepository.save(user);
    }
}
