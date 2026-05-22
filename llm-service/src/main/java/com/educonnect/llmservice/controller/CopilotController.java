package com.educonnect.llmservice.controller;

import com.educonnect.llmservice.service.CopilotService;
import com.educonnect.llmservice.service.UnifiedAgentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * REST entry point for all AI features.
 *
 * /instructor-copilot — rule-based announcement assistant for instructors (CopilotService).
 * /student-assistant  — autonomous ReAct agent for students (UnifiedAgentService).
 *                        Returns an SSE stream (text/event-stream) consumed by Next.js.
 *
 * Authentication: API Gateway validates the JWT and injects the verified user UUID
 * via the X-Authenticated-User-Id header. This service trusts that header implicitly.
 */
@RestController
@RequestMapping("/api/ai")
public class CopilotController {

    private final CopilotService copilotService;
    private final UnifiedAgentService unifiedAgentService;

    public CopilotController(CopilotService copilotService, UnifiedAgentService unifiedAgentService) {
        this.copilotService = copilotService;
        this.unifiedAgentService = unifiedAgentService;
    }

    public record ChatRequest(String message) {}
    public record ChatResponse(String reply) {}

    /**
     * Instructor copilot: parses natural-language requests and creates course announcements.
     * Rule-based — no LLM call, deterministic, low-latency.
     */
    @PostMapping("/instructor-copilot")
    public ResponseEntity<ChatResponse> askInstructorCopilot(
            @RequestHeader("X-Authenticated-User-Id") String instructorId,
            @RequestBody ChatRequest request) {

        String reply = copilotService.chatWithInstructor(request.message(), instructorId);
        return ResponseEntity.ok(new ChatResponse(reply));
    }

    /**
     * Student assistant: autonomous ReAct agent with tool calling and conversation memory.
     *
     * Returns a Server-Sent Events stream so the browser renders tokens progressively.
     * The agent autonomously decides whether to query assignments, clubs, or both —
     * no keyword matching or if-else routing involved.
     */
    @PostMapping(value = "/student-assistant", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askStudentAssistant(
            @RequestHeader("X-Authenticated-User-Id") String studentId,
            @RequestBody ChatRequest request) {

        return unifiedAgentService.chatWithStudentStream(studentId, request.message());
    }
}
