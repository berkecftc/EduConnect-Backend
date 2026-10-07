package com.educonnect.assignmentservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record GroupMemberGradeDTO(UUID studentId,
                                  String studentName,
                                  String studentNumber,
                                  BigDecimal personalGrade,
                                  BigDecimal grade,
                                  BigDecimal finalGrade) {
}
