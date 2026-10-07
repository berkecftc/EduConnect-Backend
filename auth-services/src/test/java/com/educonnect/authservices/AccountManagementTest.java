package com.educonnect.authservices;

import com.educonnect.authservices.models.AccountStatus;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.StaffGrant;
import com.educonnect.authservices.models.StaffPermission;
import com.educonnect.authservices.models.StudentStatus;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.authservices.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AuthIntegrationTest
class AccountManagementTest {

    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JWTService jwtService;

    private User manager;
    private User student;

    @BeforeEach
    void setUp() {
        manager = staff(StaffPermission.ACCOUNT_MANAGER);
        student = save("ogr", Role.ROLE_STUDENT);
        student.setStudentNumber("77" + Math.abs(UUID.randomUUID().getMostSignificantBits() % 10_000_000L));
        student = userRepository.save(student);
    }

    @Test
    void accountManagersFindAndManageStudentAndStaffAccounts() throws Exception {
        save("adm", Role.ROLE_ADMIN);
        mockMvc.perform(as(get("/api/auth/account-management/users").param("query", tag), manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(student.getId().toString()));
        mockMvc.perform(as(get("/api/auth/account-management/users").param("query", student.getStudentNumber()), manager))
                .andExpect(jsonPath("$[0].email").value(student.getEmail()));

        json(put("/api/auth/account-management/users/{id}/student-status", student.getId()), manager, "{\"status\":\"ON_LEAVE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentStatus").value("ON_LEAVE"));
        json(put("/api/auth/account-management/users/{id}/suspend", student.getId()), manager, "{\"reason\":\"Disiplin soruşturması\"}")
                .andExpect(status().isOk());
        User reloaded = userRepository.findById(student.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(reloaded.getStudentStatus()).isEqualTo(StudentStatus.ON_LEAVE);
        mockMvc.perform(as(put("/api/auth/account-management/users/{id}/reactivate", student.getId()), manager))
                .andExpect(status().isOk());
        assertThat(userRepository.findById(student.getId()).orElseThrow().getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void adminAndStaffAccountsAndOtherPermissionsStayOutOfReach() throws Exception {
        User admin = save("adm", Role.ROLE_ADMIN);
        User otherStaff = staff(StaffPermission.MODERATOR);

        json(put("/api/auth/account-management/users/{id}/suspend", admin.getId()), manager, "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TARGET_NOT_MANAGED"));
        json(put("/api/auth/account-management/users/{id}/suspend", otherStaff.getId()), manager, "{}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TARGET_NOT_MANAGED"));
        json(put("/api/auth/account-management/users/{id}/student-status", student.getId()), otherStaff, "{\"status\":\"ON_LEAVE\"}")
                .andExpect(status().isForbidden());
        json(put("/api/auth/account-management/users/{id}/suspend", student.getId()), student, "{}")
                .andExpect(status().isForbidden());
        json(put("/api/auth/account-management/users/{id}/student-status", student.getId()), admin, "{\"status\":\"ON_LEAVE\"}")
                .andExpect(status().isOk());
    }

    @Test
    void onlyAccountManagementChangesTheLoginEmail() throws Exception {
        String newEmail = "yeni-" + tag + "-" + UUID.randomUUID() + "@test.educonnect.local";
        String body = "{\"newEmail\":\"" + newEmail + "\",\"reason\":\"Kurumsal adres değişti\"}";

        json(post("/api/auth/email-change"), student, "{\"newEmail\":\"" + newEmail + "\",\"currentPassword\":\"x\"}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_CHANGE_NOT_ALLOWED"));
        mockMvc.perform(post("/api/auth/email-change/confirm").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_CHANGE_NOT_ALLOWED"));
        json(put("/api/auth/account-management/users/{id}/email", student.getId()), student, body)
                .andExpect(status().isForbidden());

        json(put("/api/auth/account-management/users/{id}/email", student.getId()), manager, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
        assertThat(userRepository.findById(student.getId()).orElseThrow().getEmail()).isEqualTo(newEmail);
        json(put("/api/auth/account-management/users/{id}/email", student.getId()), manager, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_UNCHANGED"));
        json(put("/api/auth/account-management/users/{id}/email", student.getId()), manager,
                "{\"newEmail\":\"" + manager.getEmail() + "\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("EMAIL_TAKEN"));
    }

    private ResultActions json(MockHttpServletRequestBuilder request, User actor, String body) throws Exception {
        return mockMvc.perform(as(request, actor).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, User actor) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateToken(actor));
    }

    private User staff(StaffPermission permission) {
        User user = new User("gorevli-" + UUID.randomUUID() + "@test.educonnect.local", "{noop}x", new HashSet<>(Set.of(Role.ROLE_STAFF)));
        user.getStaffGrants().add(new StaffGrant(permission, null));
        return userRepository.save(user);
    }

    private User save(String prefix, Role role) {
        return userRepository.save(new User(prefix + "-" + tag + "-" + UUID.randomUUID() + "@test.educonnect.local", "{noop}x",
                new HashSet<>(Set.of(role))));
    }
}
