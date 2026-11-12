package com.educonnect.clubservice;

import com.educonnect.clubservice.dto.request.CreateClubRequest;
import com.educonnect.clubservice.dto.request.CreateRoleChangeRequestDTO;
import com.educonnect.clubservice.dto.request.RejectRoleChangeRequestDTO;
import com.educonnect.clubservice.dto.request.RejectionReasonRequest;
import com.educonnect.clubservice.dto.request.SubmitClubRequest;
import com.educonnect.clubservice.dto.request.UpdateClubRequest;
import com.educonnect.clubservice.model.ClubPosition;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static final String LONG_TEXT = "a".repeat(256);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createClubRequestAcceptsValidSample() {
        assertThat(validator.validate(validCreateClubRequest())).isEmpty();
    }

    @Test
    void createClubRequestAcceptsMaxLengthName() {
        CreateClubRequest request = validCreateClubRequest();
        request.setName("a".repeat(255));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createClubRequestRejectsBlankName() {
        assertCreateClubViolation(request -> request.setName("  "), "name");
    }

    @Test
    void createClubRequestRejectsMissingName() {
        assertCreateClubViolation(request -> request.setName(null), "name");
    }

    @Test
    void createClubRequestRejectsTooLongName() {
        assertCreateClubViolation(request -> request.setName(LONG_TEXT), "name");
    }

    @Test
    void createClubRequestRejectsMissingAdvisor() {
        assertCreateClubViolation(request -> request.setAcademicAdvisorId(null), "academicAdvisorId");
    }

    @Test
    void createClubRequestRejectsMissingPresident() {
        assertCreateClubViolation(request -> request.setClubPresidentId(null), "clubPresidentId");
    }

    @Test
    void submitClubRequestAcceptsValidSample() {
        assertThat(validator.validate(validSubmitClubRequest())).isEmpty();
    }

    @Test
    void submitClubRequestAcceptsMissingAbout() {
        SubmitClubRequest request = validSubmitClubRequest();
        request.setAbout(null);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void submitClubRequestRejectsBlankName() {
        assertSubmitClubViolation(request -> request.setName(""), "name");
    }

    @Test
    void submitClubRequestRejectsTooLongName() {
        assertSubmitClubViolation(request -> request.setName(LONG_TEXT), "name");
    }

    @Test
    void submitClubRequestRejectsMissingAdvisor() {
        assertSubmitClubViolation(request -> request.setAcademicAdvisorId(null), "academicAdvisorId");
    }

    @Test
    void updateClubRequestAcceptsEmptyPartialUpdate() {
        assertThat(validator.validate(new UpdateClubRequest())).isEmpty();
    }

    @Test
    void updateClubRequestAcceptsValidSample() {
        UpdateClubRequest request = new UpdateClubRequest();
        request.setName("Satranç Kulübü");
        request.setAbout("Açıklama");
        request.setAcademicAdvisorId(UUID.randomUUID());
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void updateClubRequestRejectsTooLongName() {
        UpdateClubRequest request = new UpdateClubRequest();
        request.setName(LONG_TEXT);
        assertSingleViolation(validator.validate(request), "name");
    }

    @Test
    void createRoleChangeRequestAcceptsValidSample() {
        CreateRoleChangeRequestDTO request = new CreateRoleChangeRequestDTO(null, "2021001", ClubPosition.VICE_PRESIDENT);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createRoleChangeRequestRejectsMissingRole() {
        CreateRoleChangeRequestDTO request = new CreateRoleChangeRequestDTO(null, "2021001", null);
        assertSingleViolation(validator.validate(request), "requestedRole");
    }

    @Test
    void rejectRoleChangeRequestAcceptsValidSample() {
        assertThat(validator.validate(new RejectRoleChangeRequestDTO("a".repeat(255)))).isEmpty();
    }

    @Test
    void rejectRoleChangeRequestAcceptsMissingReason() {
        assertThat(validator.validate(new RejectRoleChangeRequestDTO())).isEmpty();
    }

    @Test
    void rejectRoleChangeRequestRejectsTooLongReason() {
        assertSingleViolation(validator.validate(new RejectRoleChangeRequestDTO(LONG_TEXT)), "rejectionReason");
    }

    @Test
    void rejectionReasonRequestAcceptsLongReason() {
        assertThat(validator.validate(new RejectionReasonRequest("a".repeat(2000)))).isEmpty();
    }

    private CreateClubRequest validCreateClubRequest() {
        CreateClubRequest request = new CreateClubRequest();
        request.setName("Satranç Kulübü");
        request.setAbout("Açıklama");
        request.setAcademicAdvisorId(UUID.randomUUID());
        request.setClubPresidentId(UUID.randomUUID());
        return request;
    }

    private SubmitClubRequest validSubmitClubRequest() {
        SubmitClubRequest request = new SubmitClubRequest();
        request.setName("Satranç Kulübü");
        request.setAbout("Açıklama");
        request.setAcademicAdvisorId(UUID.randomUUID());
        return request;
    }

    private void assertCreateClubViolation(Consumer<CreateClubRequest> mutation, String property) {
        CreateClubRequest request = validCreateClubRequest();
        mutation.accept(request);
        assertSingleViolation(validator.validate(request), property);
    }

    private void assertSubmitClubViolation(Consumer<SubmitClubRequest> mutation, String property) {
        SubmitClubRequest request = validSubmitClubRequest();
        mutation.accept(request);
        assertSingleViolation(validator.validate(request), property);
    }

    private static <T> void assertSingleViolation(Set<ConstraintViolation<T>> violations, String property) {
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo(property);
    }
}
