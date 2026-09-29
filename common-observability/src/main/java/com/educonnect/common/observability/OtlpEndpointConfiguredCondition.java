package com.educonnect.common.observability;

import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

class OtlpEndpointConfiguredCondition extends SpringBootCondition {

    static final String PROPERTY = "educonnect.observability.otlp-endpoint";

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String endpoint = context.getEnvironment().getProperty(PROPERTY);
        return StringUtils.hasText(endpoint)
                ? ConditionOutcome.match(PROPERTY + " is set")
                : ConditionOutcome.noMatch(PROPERTY + " is empty");
    }
}
