package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

public final class DeadlinePolicy {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private DeadlinePolicy() {
    }

    public record Window(LocalDateTime due, LocalDateTime lateUntil) {

        public boolean isLate(LocalDateTime at) {
            return due != null && at.isAfter(due);
        }

        public boolean isClosed(LocalDateTime at) {
            LocalDateTime end = lateUntil != null ? lateUntil : due;
            return end != null && at.isAfter(end);
        }
    }

    public static Window window(Assignment assignment, AssignmentExtension extension) {
        LocalDateTime due = assignment.getDueDate();
        LocalDateTime lateUntil = assignment.getLateUntil();
        if (extension == null || due == null || !extension.getDueDate().isAfter(due)) {
            return new Window(extension != null && due == null ? extension.getDueDate() : due, lateUntil);
        }
        LocalDateTime extended = extension.getDueDate();
        return new Window(extended, lateUntil == null ? null : lateUntil.plus(Duration.between(due, extended)));
    }

    public static BigDecimal finalGrade(Assignment assignment, BigDecimal grade, boolean late) {
        if (grade == null || !late || assignment.getLatePenaltyPercent().signum() == 0) {
            return grade;
        }
        return grade.multiply(HUNDRED.subtract(assignment.getLatePenaltyPercent()))
                .divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
