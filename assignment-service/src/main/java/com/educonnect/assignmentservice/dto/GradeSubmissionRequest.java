package com.educonnect.assignmentservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class GradeSubmissionRequest {
    @DecimalMin(value = "0", message = "Puan negatif olamaz")
    @Digits(integer = 4, fraction = 2, message = "Puan en fazla iki ondalık basamaklı olabilir")
    private BigDecimal grade;
    @Size(max = 5000, message = "Geri bildirim en fazla 5000 karakter olabilir")
    private String feedback;

    public GradeSubmissionRequest() {}

    public BigDecimal getGrade() { return grade; }
    public void setGrade(BigDecimal grade) { this.grade = grade; }

    public String getFeedback() { return feedback; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
}
