package com.educonnect.llmservice.controller;

import com.educonnect.llmservice.service.CopilotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class CopilotController {

    private final CopilotService copilotService;

    public CopilotController(CopilotService copilotService) {
        this.copilotService = copilotService;
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
}