package com.educonnect.userservice;

import com.educonnect.userservice.controller.InternalProfileController;
import com.educonnect.userservice.dto.request.UpdateUserProfileRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static final String LONG_TEXT = "a".repeat(256);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void updateProfileRequestAcceptsValidSample() {
        assertThat(validator.validate(validProfileRequest())).isEmpty();
    }

    @Test
    void updateProfileRequestAcceptsEmptyPartialUpdate() {
        assertThat(validator.validate(new UpdateUserProfileRequest())).isEmpty();
    }

    @Test
    void updateProfileRequestAcceptsMaxLengthValues() {
        UpdateUserProfileRequest request = validProfileRequest();
        request.setBio("a".repeat(255));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void updateProfileRequestRejectsTooLongFirstName() {
        assertProfileViolation(request -> request.setFirstName(LONG_TEXT), "firstName");
    }

    @Test
    void updateProfileRequestRejectsTooLongLastName() {
        assertProfileViolation(request -> request.setLastName(LONG_TEXT), "lastName");
    }

    @Test
    void updateProfileRequestRejectsTooLongBio() {
        assertProfileViolation(request -> request.setBio(LONG_TEXT), "bio");
    }

    @Test
    void updateProfileRequestRejectsTooLongDepartment() {
        assertProfileViolation(request -> request.setDepartment(LONG_TEXT), "department");
    }

    @Test
    void updateProfileRequestRejectsTooLongTitle() {
        assertProfileViolation(request -> request.setTitle(LONG_TEXT), "title");
    }

    @Test
    void updateProfileRequestRejectsTooLongOfficeNumber() {
        assertProfileViolation(request -> request.setOfficeNumber(LONG_TEXT), "officeNumber");
    }

    @Test
    void batchProfileIdsAcceptValidSample() throws Exception {
        assertThat(validateBatch(List.of(UUID.randomUUID(), UUID.randomUUID()))).isEmpty();
    }

    @Test
    void batchProfileIdsRejectNullBody() throws Exception {
        assertThat(paths(validateBatch(null))).containsExactly("getProfiles.userIds");
    }

    @Test
    void batchProfileIdsRejectNullElement() throws Exception {
        List<UUID> ids = new ArrayList<>(Arrays.asList(UUID.randomUUID(), null));
        assertThat(paths(validateBatch(ids))).singleElement().asString().startsWith("getProfiles.userIds[1]");
    }

    @Test
    void batchProfileIdsRejectMoreThanMaxBatchSize() throws Exception {
        List<UUID> ids = new ArrayList<>(Collections.nCopies(501, UUID.randomUUID()));
        assertThat(paths(validateBatch(ids))).containsExactly("getProfiles.userIds");
    }

    private UpdateUserProfileRequest validProfileRequest() {
        UpdateUserProfileRequest request = new UpdateUserProfileRequest();
        request.setFirstName("Ada");
        request.setLastName("Lovelace");
        request.setBio("Bilgisayar mühendisliği öğrencisi");
        request.setDepartment("Bilgisayar Mühendisliği");
        request.setTitle("Dr.");
        request.setOfficeNumber("B-204");
        return request;
    }

    private void assertProfileViolation(Consumer<UpdateUserProfileRequest> mutation, String property) {
        UpdateUserProfileRequest request = validProfileRequest();
        mutation.accept(request);
        assertThat(paths(validator.validate(request))).containsExactly(property);
    }

    private Set<ConstraintViolation<InternalProfileController>> validateBatch(List<UUID> ids) throws Exception {
        Method method = InternalProfileController.class.getMethod("getProfiles", List.class);
        return validator.forExecutables().validateParameters(
                new InternalProfileController(null), method, new Object[]{ids});
    }

    private static List<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath().toString()).toList();
    }
}
