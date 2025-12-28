package com.educonnect.assignmentservice.dto;

public class GradeSubmissionRequest {
    private Integer grade; // 0-100
    private String feedback;

    public GradeSubmissionRequest() {}

    // Getters and Setters
    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
}

