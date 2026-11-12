package com.educonnect.clubservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FoundingSettings {

    private final int minMembers;

    public FoundingSettings(@Value("${educonnect.club.founding.min-members:1}") int minMembers) {
        this.minMembers = Math.max(1, minMembers);
    }

    public int minMembers() {
        return minMembers;
    }
}
