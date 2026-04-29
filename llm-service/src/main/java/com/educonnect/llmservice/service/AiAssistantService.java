package com.educonnect.llmservice.service;

import com.educonnect.llmservice.client.AssignmentServiceClient;
import feign.FeignException;
import org.springframework.stereotype.Service;

/**
 * Bu servis, öğrencilere akademik danışmanlık yapmak, ödevlerini sorgulamak
 * ve kulüp/etkinlik tavsiyeleri vermek üzere özelleştirilmiş AI asistanıdır.
 */
@Service
public class AiAssistantService {

    private final AssignmentServiceClient assignmentServiceClient;

    public AiAssistantService(AssignmentServiceClient assignmentServiceClient) {
        this.assignmentServiceClient = assignmentServiceClient;
    }

    /**
     * Öğrenci ile chatbot arasındaki iletişimi yönetir.
     * * @param userMessage Öğrencinin yazdığı mesaj.
     * @param studentId   API Gateway'den gelen ve veritabanı sorguları için kullanılacak gerçek öğrenci UUID'si.
     * @return AI tarafından üretilen veya araçlar (tools) kullanılarak getirilen cevap.
     */
    public String chatWithStudent(String userMessage, String studentId) {

        if (studentId == null || studentId.isBlank()) {
            return "Kimlik doğrulanamadı. Lütfen tekrar giriş yapıp isteği yeniden deneyin.";
        }

        java.util.List<AssignmentServiceClient.AssignmentResponse> assignments;
        try {
            assignments = assignmentServiceClient.getMyAssignments(studentId);
        } catch (FeignException ex) {
            return "Ödev bilgileri alınamadı. Lütfen daha sonra tekrar deneyin.";
        }

        if (assignments == null || assignments.isEmpty()) {
            return "Şu anda teslim etmeniz gereken bir ödev görünmüyor.";
        }

        var pendingAssignments = assignments.stream()
                .filter(assignment -> !isSubmitted(assignment))
                .filter(assignment -> !isPastDue(assignment.dueDate()))
                .toList();

        if (pendingAssignments.isEmpty()) {
            return "Şu anda teslim etmeniz gereken bir ödev görünmüyor.";
        }

        StringBuilder response = new StringBuilder();
        for (var assignment : pendingAssignments) {
            String courseName = assignment.courseId() == null || assignment.courseId().isBlank()
                    ? "ilgili ders"
                    : "ders";
            String dueDate = formatDueDate(assignment.dueDate());
            String title = assignment.title() == null || assignment.title().isBlank()
                    ? "ilgili"
                    : assignment.title().trim();

            if (!response.isEmpty()) {
                response.append("\n");
            }
            response.append(String.format(
                    "Merhaba, %s için %s ödevini %s tarihine kadar teslim etmeniz gerekiyor.",
                    courseName,
                    title,
                    dueDate
            ));
        }

        return response.toString();
    }

    private boolean isSubmitted(AssignmentServiceClient.AssignmentResponse assignment) {
        return assignment.submission() != null && assignment.submission().submissionId() != null;
    }

    private boolean isPastDue(String dueDate) {
        if (dueDate == null || dueDate.isBlank()) {
            return false;
        }

        try {
            java.time.LocalDateTime parsed = java.time.LocalDateTime.parse(dueDate);
            return parsed.isBefore(java.time.LocalDateTime.now());
        } catch (java.time.format.DateTimeParseException ex) {
            try {
                java.time.OffsetDateTime parsed = java.time.OffsetDateTime.parse(dueDate);
                return parsed.toLocalDateTime().isBefore(java.time.LocalDateTime.now());
            } catch (java.time.format.DateTimeParseException nested) {
                return false;
            }
        }
    }

    private String formatDueDate(String dueDate) {
        if (dueDate == null || dueDate.isBlank()) {
            return "belirtilen";
        }

        try {
            java.time.LocalDateTime parsed = java.time.LocalDateTime.parse(dueDate);
            java.time.format.DateTimeFormatter formatter =
                    java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            return parsed.format(formatter);
        } catch (java.time.format.DateTimeParseException ex) {
            try {
                java.time.OffsetDateTime parsed = java.time.OffsetDateTime.parse(dueDate);
                java.time.format.DateTimeFormatter formatter =
                        java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
                return parsed.format(formatter);
            } catch (java.time.format.DateTimeParseException nested) {
                return dueDate.trim();
            }
        }
    }
}