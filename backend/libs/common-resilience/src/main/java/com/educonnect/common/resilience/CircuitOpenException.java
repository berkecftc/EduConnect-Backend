package com.educonnect.common.resilience;

import java.io.IOException;

public class CircuitOpenException extends IOException {

    private final String target;

    public CircuitOpenException(String target) {
        super("Circuit breaker open for " + target);
        this.target = target;
    }

    public String target() {
        return target;
    }
}
