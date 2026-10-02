package com.educonnect.assignmentservice.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record GroupSetResponse(UUID id, UUID courseId, String name, boolean selfSignup, Integer maxMembers,
                               LocalDateTime signupClosesAt, boolean signupOpen, UUID myGroupId, List<Group> groups) {

    public record Group(UUID id, String name, int memberCount, List<Member> members) {
    }

    public record Member(UUID studentId, String name, String studentNumber, Instant joinedAt) {
    }
}
