package com.educonnect.clubservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class FinanceSettings {

    private final BigDecimal advisorThreshold;

    public FinanceSettings(@Value("${educonnect.club.finance.advisor-threshold:1000}") BigDecimal advisorThreshold) {
        this.advisorThreshold = advisorThreshold;
    }

    public boolean needsAdvisor(BigDecimal amount) {
        return amount.compareTo(advisorThreshold) > 0;
    }

    public BigDecimal advisorThreshold() {
        return advisorThreshold;
    }
}
