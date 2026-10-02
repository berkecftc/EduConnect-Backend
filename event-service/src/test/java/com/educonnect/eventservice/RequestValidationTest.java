package com.educonnect.eventservice;

import com.educonnect.eventservice.dto.request.CreateEventRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    private static final String LONG_TEXT = "a".repeat(256);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createEventRequestAcceptsValidSample() {
        assertThat(validator.validate(validCreateEventRequest())).isEmpty();
    }

    @Test
    void createEventRequestAcceptsOptionalFieldsMissing() {
        CreateEventRequest request = validCreateEventRequest();
        request.setDescription(null);
        request.setLocation(null);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createEventRequestAcceptsMaxLengthValues() {
        CreateEventRequest request = validCreateEventRequest();
        request.setTitle("a".repeat(255));
        request.setLocation("a".repeat(255));
        request.setClubName("a".repeat(255));
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void createEventRequestRejectsBlankTitle() {
        assertCreateEventViolation(request -> request.setTitle(" "), "title");
    }

    @Test
    void createEventRequestRejectsMissingTitle() {
        assertCreateEventViolation(request -> request.setTitle(null), "title");
    }

    @Test
    void createEventRequestRejectsTooLongTitle() {
        assertCreateEventViolation(request -> request.setTitle(LONG_TEXT), "title");
    }

    @Test
    void createEventRequestRejectsMissingStart() {
        assertCreateEventViolation(request -> request.setStartsAt(null), "startsAt");
    }

    @Test
    void createEventRequestRejectsTooLongLocation() {
        assertCreateEventViolation(request -> request.setLocation(LONG_TEXT), "location");
    }

    @Test
    void createEventRequestRejectsBlankClubName() {
        assertCreateEventViolation(request -> request.setClubName(""), "clubName");
    }

    @Test
    void createEventRequestRejectsMissingClubName() {
        assertCreateEventViolation(request -> request.setClubName(null), "clubName");
    }

    @Test
    void createEventRequestRejectsTooLongClubName() {
        assertCreateEventViolation(request -> request.setClubName(LONG_TEXT), "clubName");
    }

    private CreateEventRequest validCreateEventRequest() {
        CreateEventRequest request = new CreateEventRequest();
        request.setTitle("Tanışma Toplantısı");
        request.setDescription("Açıklama");
        request.setStartsAt(LocalDateTime.now().plusDays(3));
        request.setLocation("Konferans Salonu");
        request.setClubName("Satranç Kulübü");
        return request;
    }

    private void assertCreateEventViolation(Consumer<CreateEventRequest> mutation, String property) {
        CreateEventRequest request = validCreateEventRequest();
        mutation.accept(request);
        Set<ConstraintViolation<CreateEventRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo(property);
    }
}
