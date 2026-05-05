package com.educonnect.llmservice.controller;

import com.educonnect.llmservice.service.AiAssistantService;
import com.educonnect.llmservice.service.ClubRecommendationService;
import com.educonnect.llmservice.service.CopilotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class CopilotController {

    private final CopilotService copilotService;
    private final AiAssistantService aiAssistantService;
    private final ClubRecommendationService clubRecommendationService;

    public CopilotController(CopilotService copilotService, AiAssistantService aiAssistantService,
                             ClubRecommendationService clubRecommendationService) {
        this.copilotService = copilotService;
        this.aiAssistantService = aiAssistantService;
        this.clubRecommendationService = clubRecommendationService;
    }

    public record ChatRequest(String message) {}
    public record ChatResponse(String reply) {}

    @PostMapping("/instructor-copilot")
    public ResponseEntity<ChatResponse> askCopilot(
            @RequestHeader("X-Authenticated-User-Id") String instructorId,
            @RequestBody ChatRequest request) {

        // Gelen mesajı ve Gateway'den gelen güvenli ID'yi LLM'e veriyoruz
        String llmReply = copilotService.chatWithInstructor(request.message(), instructorId);

        return ResponseEntity.ok(new ChatResponse(llmReply));
    }

    @PostMapping("/club-assistant")
    public ResponseEntity<ChatResponse> askClubAssistant(@RequestBody ChatRequest request) {
        String llmReply = clubRecommendationService.recommendClub(request.message());
        return ResponseEntity.ok(new ChatResponse(llmReply));
    }

    @PostMapping("/student-assistant")
    public ResponseEntity<ChatResponse> askStudentAssistant(
            @RequestHeader("X-Authenticated-User-Id") String studentId,
            @RequestBody ChatRequest request) {

        String reply = aiAssistantService.chatWithStudent(request.message(), studentId);
        return ResponseEntity.ok(new ChatResponse(reply));
    }
}