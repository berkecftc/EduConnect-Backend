package com.educonnect.authservices;

import com.educonnect.authservices.controller.InternalAuthController;
import com.educonnect.authservices.dto.request.ChangePasswordRequest;
import com.educonnect.authservices.dto.request.ForgotPasswordRequest;
import com.educonnect.authservices.dto.request.LoginRequest;
import com.educonnect.authservices.dto.request.RefreshTokenRequest;
import com.educonnect.authservices.dto.request.RegisterRequest;
import com.educonnect.authservices.dto.request.ResendVerificationRequest;
import com.educonnect.authservices.dto.request.ResetPasswordRequest;
import com.educonnect.authservices.dto.request.SuspendAccountRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<String> violatedPaths(Object target) {
        return validator.validate(target).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }

    private static String repeat(int length) {
        return "a".repeat(length);
    }

    private static RegisterRequest register(Consumer<RegisterRequest> customizer) {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("ayse@example.edu");
        request.setPassword("GucluSifre!2024");
        request.setFirstName("Ayşe");
        request.setLastName("Yılmaz");
        request.setStudentId("20231234");
        request.setDepartment("Bilgisayar Mühendisliği");
        request.setTitle("Dr.");
        request.setOfficeNumber("A-101");
        customizer.accept(request);
        return request;
    }

    @Test
    void registerRequest_validSamplePasses() {
        assertThat(violatedPaths(register(r -> { }))).isEmpty();
        assertThat(violatedPaths(register(r -> {
            r.setStudentId(null);
            r.setDepartment(null);
            r.setTitle(null);
            r.setOfficeNumber(null);
        }))).isEmpty();
    }

    @Test
    void registerRequest_eachConstraintRejects() {
        assertThat(violatedPaths(register(r -> r.setEmail(null)))).containsExactly("email");
        assertThat(violatedPaths(register(r -> r.setEmail(" ")))).contains("email");
        assertThat(violatedPaths(register(r -> r.setEmail("gecersiz-eposta")))).containsExactly("email");
        assertThat(violatedPaths(register(r -> r.setEmail(repeat(250) + "@x.edu")))).contains("email");
        assertThat(violatedPaths(register(r -> r.setPassword(null)))).containsExactly("password");
        assertThat(violatedPaths(register(r -> r.setPassword("")))).containsExactly("password");
        assertThat(violatedPaths(register(r -> r.setFirstName(" ")))).containsExactly("firstName");
        assertThat(violatedPaths(register(r -> r.setFirstName(repeat(256))))).containsExactly("firstName");
        assertThat(violatedPaths(register(r -> r.setLastName(null)))).containsExactly("lastName");
        assertThat(violatedPaths(register(r -> r.setLastName(repeat(256))))).containsExactly("lastName");
        assertThat(violatedPaths(register(r -> r.setStudentId(repeat(256))))).containsExactly("studentId");
        assertThat(violatedPaths(register(r -> r.setDepartment(repeat(256))))).containsExactly("department");
        assertThat(violatedPaths(register(r -> r.setTitle(repeat(256))))).containsExactly("title");
        assertThat(violatedPaths(register(r -> r.setOfficeNumber(repeat(256))))).containsExactly("officeNumber");
    }

    private static LoginRequest login(String email, String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    @Test
    void loginRequest_validatesPresence() {
        assertThat(violatedPaths(login("admin", "sifre"))).isEmpty();
        assertThat(violatedPaths(login(" ", "sifre"))).containsExactly("email");
        assertThat(violatedPaths(login(repeat(256), "sifre"))).containsExactly("email");
        assertThat(violatedPaths(login("ayse@example.edu", ""))).containsExactly("password");
        assertThat(violatedPaths(login("ayse@example.edu", null))).containsExactly("password");
    }

    private static ChangePasswordRequest changePassword(String current, String next, String confirmation) {
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        request.setConfirmationPassword(confirmation);
        return request;
    }

    @Test
    void changePasswordRequest_validatesPresence() {
        assertThat(violatedPaths(changePassword("eski", "yeni", "yeni"))).isEmpty();
        assertThat(violatedPaths(changePassword(null, "yeni", "yeni"))).containsExactly("currentPassword");
        assertThat(violatedPaths(changePassword("eski", "", "yeni"))).containsExactly("newPassword");
        assertThat(violatedPaths(changePassword("eski", "yeni", null))).containsExactly("confirmationPassword");
    }

    @Test
    void resetPasswordRequest_validatesPresence() {
        assertThat(violatedPaths(new ResetPasswordRequest("token", "yeni", "yeni"))).isEmpty();
        assertThat(violatedPaths(new ResetPasswordRequest(" ", "yeni", "yeni"))).containsExactly("token");
        assertThat(violatedPaths(new ResetPasswordRequest("token", null, "yeni"))).containsExactly("newPassword");
        assertThat(violatedPaths(new ResetPasswordRequest("token", "yeni", ""))).containsExactly("confirmPassword");
    }

    @Test
    void forgotPasswordRequest_validatesEmail() {
        assertThat(violatedPaths(new ForgotPasswordRequest("ayse@example.edu"))).isEmpty();
        assertThat(violatedPaths(new ForgotPasswordRequest(null))).containsExactly("email");
        assertThat(violatedPaths(new ForgotPasswordRequest("gecersiz"))).containsExactly("email");
        assertThat(violatedPaths(new ForgotPasswordRequest(repeat(250) + "@x.edu"))).contains("email");
    }

    @Test
    void resendVerificationRequest_validatesEmail() {
        assertThat(violatedPaths(new ResendVerificationRequest("ayse@example.edu"))).isEmpty();
        assertThat(violatedPaths(new ResendVerificationRequest(""))).containsExactly("email");
        assertThat(violatedPaths(new ResendVerificationRequest("gecersiz"))).containsExactly("email");
        assertThat(violatedPaths(new ResendVerificationRequest(repeat(250) + "@x.edu"))).contains("email");
    }

    @Test
    void refreshTokenRequest_requiresToken() {
        assertThat(violatedPaths(new RefreshTokenRequest("token"))).isEmpty();
        assertThat(violatedPaths(new RefreshTokenRequest(null))).containsExactly("refreshToken");
        assertThat(violatedPaths(new RefreshTokenRequest(" "))).containsExactly("refreshToken");
        assertThat(validator.validate(new RefreshTokenRequest(null)))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("Refresh token is required");
    }

    @Test
    void suspendAccountRequest_limitsReasonLength() {
        assertThat(violatedPaths(new SuspendAccountRequest(null))).isEmpty();
        assertThat(violatedPaths(new SuspendAccountRequest(repeat(500)))).isEmpty();
        assertThat(violatedPaths(new SuspendAccountRequest(repeat(501)))).containsExactly("reason");
    }

    @Test
    void internalEmailLookup_rejectsNullListAndNullIds() throws Exception {
        InternalAuthController controller = new InternalAuthController(null, null, null);
        Method method = InternalAuthController.class.getMethod("getEmailsByIds", List.class);

        assertThat(validator.forExecutables()
                .validateParameters(controller, method, new Object[]{List.of(UUID.randomUUID())})).isEmpty();

        Set<String> nullList = validator.forExecutables()
                .validateParameters(controller, method, new Object[]{null}).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
        assertThat(nullList).singleElement().asString().startsWith("getEmailsByIds.");

        Set<String> nullElement = validator.forExecutables()
                .validateParameters(controller, method, new Object[]{Arrays.asList(UUID.randomUUID(), null)}).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(Collectors.toSet());
        assertThat(nullElement).singleElement().asString().startsWith("getEmailsByIds.").contains("[1]");
    }
}
