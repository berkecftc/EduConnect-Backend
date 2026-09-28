package com.educonnect.assignmentservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public class GradeSubmissionRequest {
    @Min(value = 0, message = "Not 0-100 arasında olmalıdır")
    @Max(value = 100, message = "Not 0-100 arasında olmalıdır")
    private Integer grade; // 0-100
    private String feedback;

    public GradeSubmissionRequest() {}

    // Getters and Setters
    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
}

