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

        var assignments = java.util.Collections.<AssignmentServiceClient.AssignmentResponse>emptyList();
        try {
            assignments = assignmentServiceClient.getUserAssignments(studentId);
        } catch (FeignException.NotFound ex) {
            // Kullanıcının ödevi yoksa 404 dönüyor; bunu boş liste olarak kabul ediyoruz.
        }

        if (assignments == null || assignments.isEmpty()) {
            return "Şu anda teslim etmeniz gereken bir ödev görünmüyor.";
        }

        StringBuilder response = new StringBuilder();
        for (var assignment : assignments) {
            String courseName = assignment.courseName() == null || assignment.courseName().isBlank()
                    ? "ders"
                    : assignment.courseName();
            String dueDate = assignment.dueDate() == null || assignment.dueDate().isBlank()
                    ? "belirtilen tarihe"
                    : assignment.dueDate();
            String title = assignment.title() == null || assignment.title().isBlank()
                    ? "belirtilen"
                    : assignment.title();

            if (!response.isEmpty()) {
                response.append("\n");
            }
            response.append(String.format(
                    "Merhaba, %s dersinden %s tarihine kadar %s ödevini teslim etmeniz gerekiyor.",
                    courseName,
                    dueDate,
                    title
            ));
        }

        return response.toString();
    }
}