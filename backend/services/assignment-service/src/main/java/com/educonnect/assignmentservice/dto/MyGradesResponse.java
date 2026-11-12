package com.educonnect.assignmentservice.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MyGradesResponse(UUID courseId, List<GradebookResponse.Column> assessments, List<GradebookResponse.Cell> grades,
                               BigDecimal weightedTotal, BigDecimal gradedWeight) {
}
