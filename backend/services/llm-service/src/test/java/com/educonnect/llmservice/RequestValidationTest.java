package com.educonnect.llmservice;

import com.educonnect.llmservice.controller.CopilotController.ChatRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
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

    @Test
    void chatRequest_validSamplePasses() {
        assertThat(violatedPaths(new ChatRequest("Bu haftaki ödevlerim neler?"))).isEmpty();
    }

    @Test
    void chatRequest_rejectsMissingOrBlankMessage() {
        assertThat(violatedPaths(new ChatRequest(null))).containsExactly("message");
        assertThat(violatedPaths(new ChatRequest(""))).containsExactly("message");
        assertThat(violatedPaths(new ChatRequest("   "))).containsExactly("message");
    }
}
