package com.educonnect.authservices;

import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.StaffGrant;
import com.educonnect.authservices.models.StaffPermission;
import com.educonnect.authservices.models.StudentRegistrationRequest;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.authservices.service.InstitutionPolicy;
import com.educonnect.authservices.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AuthIntegrationTest
class VerificationTest {

    private final UUID engineering = UUID.randomUUID();
    private final UUID medicine = UUID.randomUUID();
    private final UUID computerProgram = UUID.randomUUID();
    private final UUID medicineProgram = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentRequestRepository studentRequestRepository;

    @Autowired
    private JWTService jwtService;

    @MockitoBean
    private InstitutionPolicy institutionPolicy;

    private StudentRegistrationRequest computer;
    private StudentRegistrationRequest doctor;
    private StudentRegistrationRequest unplaced;

    @BeforeEach
    void setUp() {
        when(institutionPolicy.facultyOfProgram(computerProgram)).thenReturn(engineering);
        when(institutionPolicy.facultyOfProgram(medicineProgram)).thenReturn(medicine);
        computer = request(computerProgram);
        doctor = request(medicineProgram);
        unplaced = request(null);
    }

    @Test
    void studentVerifiersSeeAndDecideOnlyTheirFaculties() throws Exception {
        User engineeringDesk = staff(new StaffGrant(StaffPermission.STUDENT_VERIFIER, engineering));

        mockMvc.perform(get("/api/auth/verification/students").header(HttpHeaders.AUTHORIZATION, bearer(engineeringDesk)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(computer.getId().intValue())))
                .andExpect(jsonPath("$[*].id", not(hasItem(doctor.getId().intValue()))))
                .andExpect(jsonPath("$[*].id", not(hasItem(unplaced.getId().intValue()))));
        mockMvc.perform(post("/api/auth/verification/students/{id}/approve", doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineeringDesk)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("OUT_OF_SCOPE"));
        mockMvc.perform(post("/api/auth/verification/students/{id}/approve", computer.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(engineeringDesk)))
                .andExpect(status().isOk());
        assertThat(userRepository.findByEmail(computer.getEmail()).orElseThrow().getRoles()).containsExactly(Role.ROLE_STUDENT);

        User wholeInstitution = staff(new StaffGrant(StaffPermission.STUDENT_VERIFIER, null));
        mockMvc.perform(get("/api/auth/verification/students").header(HttpHeaders.AUTHORIZATION, bearer(wholeInstitution)))
                .andExpect(jsonPath("$[*].id", hasItem(doctor.getId().intValue())))
                .andExpect(jsonPath("$[*].id", hasItem(unplaced.getId().intValue())));
        mockMvc.perform(post("/api/auth/verification/students/{id}/reject", unplaced.getId())
                        .param("reason", "Belge okunmuyor")
                        .header(HttpHeaders.AUTHORIZATION, bearer(wholeInstitution)))
                .andExpect(status().isOk());
        assertThat(studentRequestRepository.findById(unplaced.getId())).isEmpty();
    }

    @Test
    void eachVerificationNeedsItsOwnPermission() throws Exception {
        User studentDesk = staff(new StaffGrant(StaffPermission.STUDENT_VERIFIER, null));
        User staffDesk = staff(new StaffGrant(StaffPermission.STAFF_VERIFIER, null));
        User student = userRepository.save(new User("s-" + UUID.randomUUID() + "@test.educonnect.local", "{noop}x",
                new HashSet<>(Set.of(Role.ROLE_STUDENT))));
        User admin = userRepository.save(new User("a-" + UUID.randomUUID() + "@test.educonnect.local", "{noop}x",
                new HashSet<>(Set.of(Role.ROLE_ADMIN))));

        mockMvc.perform(get("/api/auth/verification/academicians").header(HttpHeaders.AUTHORIZATION, bearer(studentDesk)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/verification/students").header(HttpHeaders.AUTHORIZATION, bearer(staffDesk)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/verification/students").header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/auth/verification/academicians").header(HttpHeaders.AUTHORIZATION, bearer(staffDesk)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/verification/students").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(jsonPath("$[*].id", hasItem(unplaced.getId().intValue())));
    }

    private StudentRegistrationRequest request(UUID programId) {
        StudentRegistrationRequest request = new StudentRegistrationRequest();
        request.setFirstName("Aday");
        request.setLastName("Öğrenci");
        request.setEmail("aday-" + UUID.randomUUID() + "@test.educonnect.local");
        request.setPassword("{noop}x");
        request.setStudentNumber(String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L)));
        request.setProgramId(programId);
        request.setEmailVerifiedAt(Instant.now());
        request.setStudentDocumentUrl("student-documents/" + UUID.randomUUID() + ".pdf");
        return studentRequestRepository.save(request);
    }

    private User staff(StaffGrant grant) {
        User user = new User("gorevli-" + UUID.randomUUID() + "@test.educonnect.local", "{noop}x", new HashSet<>(Set.of(Role.ROLE_STAFF)));
        user.getStaffGrants().add(grant);
        return userRepository.save(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }
}
