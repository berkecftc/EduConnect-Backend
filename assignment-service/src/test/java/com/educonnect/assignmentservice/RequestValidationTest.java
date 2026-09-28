package com.educonnect.assignmentservice;

import com.educonnect.assignmentservice.dto.AssignmentRequest;
import com.educonnect.assignmentservice.dto.GradeSubmissionRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void assignmentRequestAcceptsValidSample() {
        assertThat(validator.validate(validAssignmentRequest())).isEmpty();
    }

    @Test
    void assignmentRequestAcceptsMissingDescription() {
        AssignmentRequest request = validAssignmentRequest();
        request.setDescription(null);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void assignmentRequestRejectsBlankTitle() {
        assertAssignmentViolation(request -> request.setTitle(" "), "title");
    }

    @Test
    void assignmentRequestRejectsTooLongTitle() {
        assertAssignmentViolation(request -> request.setTitle("a".repeat(256)), "title");
    }

    @Test
    void assignmentRequestRejectsMissingDueDate() {
        assertAssignmentViolation(request -> request.setDueDate(null), "dueDate");
    }

    @Test
    void assignmentRequestRejectsMissingCourseId() {
        assertAssignmentViolation(request -> request.setCourseId(null), "courseId");
    }

    @Test
    void gradeRequestAcceptsBoundaryGrades() {
        assertThat(validator.validate(gradeRequest(0))).isEmpty();
        assertThat(validator.validate(gradeRequest(100))).isEmpty();
    }

    @Test
    void gradeRequestAcceptsMissingGrade() {
        assertThat(validator.validate(gradeRequest(null))).isEmpty();
    }

    @Test
    void gradeRequestRejectsNegativeGrade() {
        assertThat(paths(validator.validate(gradeRequest(-1)))).containsExactly("grade");
    }

    @Test
    void gradeRequestRejectsGradeAboveHundred() {
        assertThat(paths(validator.validate(gradeRequest(101)))).containsExactly("grade");
    }

    private AssignmentRequest validAssignmentRequest() {
        AssignmentRequest request = new AssignmentRequest();
        request.setTitle("Ödev 1");
        request.setDescription("Bağlı liste uygulaması");
        request.setDueDate(LocalDateTime.now().plusDays(7));
        request.setCourseId(UUID.randomUUID());
        return request;
    }

    private GradeSubmissionRequest gradeRequest(Integer grade) {
        GradeSubmissionRequest request = new GradeSubmissionRequest();
        request.setGrade(grade);
        request.setFeedback("İyi iş");
        return request;
    }

    private void assertAssignmentViolation(Consumer<AssignmentRequest> mutation, String property) {
        AssignmentRequest request = validAssignmentRequest();
        mutation.accept(request);
        assertThat(paths(validator.validate(request))).containsExactly(property);
    }

    private static List<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(violation -> violation.getPropertyPath().toString()).toList();
    }
}
