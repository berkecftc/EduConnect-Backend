package com.educonnect.courseservice;

import com.educonnect.courseservice.dto.AnnouncementRequest;
import com.educonnect.courseservice.dto.CourseRequest;
import com.educonnect.courseservice.dto.RejectApplicationRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static final String LONG_TEXT = "a".repeat(256);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void courseRequestAcceptsValidSample() {
        assertThat(validator.validate(validCourseRequest())).isEmpty();
    }

    @Test
    void courseRequestAcceptsMissingOptionalFields() {
        CourseRequest request = validCourseRequest();
        request.setDescription(null);
        request.setSemester(null);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void courseRequestRejectsBlankTitle() {
        assertCourseViolation(request -> request.setTitle(" "), "title");
    }

    @Test
    void courseRequestRejectsTooLongTitle() {
        assertCourseViolation(request -> request.setTitle(LONG_TEXT), "title");
    }

    @Test
    void courseRequestRejectsBlankCode() {
        assertCourseViolation(request -> request.setCode(""), "code");
    }

    @Test
    void courseRequestRejectsTooLongCode() {
        assertCourseViolation(request -> request.setCode(LONG_TEXT), "code");
    }

    @Test
    void courseRequestRejectsZeroCredit() {
        assertCourseViolation(request -> request.setCredit(0), "credit");
    }

    @Test
    void courseRequestRejectsTooLongSemester() {
        assertCourseViolation(request -> request.setSemester(LONG_TEXT), "semester");
    }

    @Test
    void courseRequestRejectsMissingInstructor() {
        assertCourseViolation(request -> request.setInstructorId(null), "instructorId");
    }

    @Test
    void courseRequestRejectsZeroCapacity() {
        assertCourseViolation(request -> request.setCapacity(0), "capacity");
    }

    @Test
    void announcementRequestAcceptsValidSample() {
        assertThat(validator.validate(validAnnouncementRequest())).isEmpty();
    }

    @Test
    void announcementRequestRejectsBlankTitle() {
        AnnouncementRequest request = validAnnouncementRequest();
        request.setTitle(null);
        assertThat(paths(validator.validate(request))).containsExactly("title");
    }

    @Test
    void announcementRequestRejectsTooLongTitle() {
        AnnouncementRequest request = validAnnouncementRequest();
        request.setTitle(LONG_TEXT);
        assertThat(paths(validator.validate(request))).containsExactly("title");
    }

    @Test
    void announcementRequestRejectsBlankContent() {
        AnnouncementRequest request = validAnnouncementRequest();
        request.setContent("  ");
        assertThat(paths(validator.validate(request))).containsExactly("content");
    }

    @Test
    void rejectApplicationRequestAcceptsValidSample() {
        RejectApplicationRequest request = new RejectApplicationRequest();
        request.setRejectionReason("Kontenjan doldu");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectApplicationRequestAcceptsMissingReason() {
        assertThat(validator.validate(new RejectApplicationRequest())).isEmpty();
    }

    @Test
    void rejectApplicationRequestRejectsTooLongReason() {
        RejectApplicationRequest request = new RejectApplicationRequest();
        request.setRejectionReason(LONG_TEXT);
        assertThat(paths(validator.validate(request))).containsExactly("rejectionReason");
    }

    private CourseRequest validCourseRequest() {
        CourseRequest request = new CourseRequest();
        request.setTitle("Veri Yapıları");
        request.setCode("BIL201");
        request.setDescription("Temel veri yapıları");
        request.setCredit(3);
        request.setSemester("2026 Güz");
        request.setInstructorId(UUID.randomUUID());
        request.setCapacity(30);
        return request;
    }

    private AnnouncementRequest validAnnouncementRequest() {
        AnnouncementRequest request = new AnnouncementRequest();
        request.setTitle("Vize tarihi");
        request.setContent("Vize sınavı gelecek hafta yapılacaktır.");
        return request;
    }

    private void assertCourseViolation(Consumer<CourseRequest> mutation, String property) {
        CourseRequest request = validCourseRequest();
        mutation.accept(request);
        assertThat(paths(validator.validate(request))).containsExactly(property);
    }

    @Test
    void rejectApplication_acceptsFrontendReasonField() throws Exception {
        RejectApplicationRequest request = new ObjectMapper()
                .readValue("{\"reason\":\"Kontenjan doldu\"}", RejectApplicationRequest.class);

        assertThat(request.getRejectionReason()).isEqualTo("Kontenjan doldu");
    }

    private static List<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath().toString()).toList();
    }
}
