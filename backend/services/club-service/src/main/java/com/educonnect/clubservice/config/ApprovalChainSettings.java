package com.educonnect.clubservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ApprovalChainSettings {

    private final boolean enabled;

    public ApprovalChainSettings(@Value("${educonnect.club.approval-chain.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }
}
