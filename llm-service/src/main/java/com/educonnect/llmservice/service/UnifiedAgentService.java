package com.educonnect.llmservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Arrays;

/**
 * Unified Autonomous Student Agent.
 *
 * Architecture: ReAct (Reason + Act) loop via Spring AI's ChatClient.
 *
 * The agent follows this internal cycle per request:
 *   1. REASON  — LLM reads the system prompt, user message, and conversation history.
 *   2. ACT     — If a tool is needed, LLM emits a tool-call; Spring AI executes it synchronously.
 *   3. OBSERVE — Tool result is appended to the context; LLM reasons again.
 *   4. RESPOND — LLM streams its final answer back as Flux<String> (SSE to the browser).
 *
 * Steps 2–3 repeat until the LLM decides no more tools are needed (self-termination).
 *
 * Self-Correction via Memory:
 *   MessageChatMemoryAdvisor injects the last N conversation turns before each request.
 *   If a previous tool call failed (e.g., missing parameter), the LLM sees the failure
 *   in history and retries with the correct argument — no developer intervention needed.
 *
 * Caching strategy:
 *   Cache key = studentId + "::" + userMessage, which prevents cross-student contamination.
 *   Only the final synthesised text is cached once the stream completes.
 */
@Service
public class UnifiedAgentService {

    private static final Logger log = LoggerFactory.getLogger(UnifiedAgentService.class);

    /**
     * Number of past messages the memory advisor injects into each request.
     * 10 messages ≈ 5 conversation turns — enough for self-correction without
     * overwhelming the small context window of llama3.2:1b.
     */
    private static final int MEMORY_WINDOW_SIZE = 10;

    /**
     * Spring bean names declared in AiToolsConfig.
     * These must match the @Bean method names exactly.
     */
    private static final String[] STUDENT_TOOLS = {"getAssignmentsTool", "searchClubsTool"};

    /**
     * System prompt that drives the ReAct reasoning loop.
     * The {studentId} placeholder is substituted at request time.
     *
     * Prompt engineering notes:
     *  - Tool descriptions live in @Description on each bean (AiToolsConfig).
     *    This prompt focuses on persona, language, boundaries, and identity injection.
     *  - The studentId injection is the mechanism that gives tools the caller's identity.
     */
    private static final String STUDENT_SYSTEM_PROMPT = """
            You are a helpful academic assistant for EduConnect, a university education platform.
            Always respond in Turkish. Be friendly, concise, and accurate.

            Current student ID: {studentId}

            You have access to two tools:
            - getAssignmentsTool: retrieves the student's pending assignments.
              Always pass studentId = "{studentId}" when calling this tool.
            - searchClubsTool: searches university clubs by topic or interest keyword.

            Strict rules you must follow:
            1. Use getAssignmentsTool ONLY for questions about assignments, homework, deadlines, or submissions.
            2. Use searchClubsTool ONLY for questions about clubs, communities, or student activities.
            3. Never fabricate data. If a tool returns an empty list, say so clearly in Turkish.
            4. Never reveal internal tool names, student IDs, or technical error details to the student.
            5. If the question is outside your scope, politely explain what you can help with.
            """;

    private final ChatClient agentChatClient;
    private final SemanticCacheService semanticCacheService;

    /**
     * The ChatClient is built once and shared across all requests.
     * InMemoryChatMemory isolates each student's history via conversationId (= studentId),
     * which is set per-request through the advisor spec.
     */
    public UnifiedAgentService(
            ChatClient.Builder chatClientBuilder,
            SemanticCacheService semanticCacheService) {
        this.semanticCacheService = semanticCacheService;
        this.agentChatClient = chatClientBuilder
                .defaultAdvisors(new MessageChatMemoryAdvisor(new InMemoryChatMemory()))
                .build();
    }

    /**
     * Entry point: streams the agent's response for a student message.
     *
     * @param studentId   The authenticated student's UUID, injected by the API Gateway header.
     * @param userMessage The raw message text from the student.
     * @return Flux<String> token stream, suitable for Server-Sent Events (SSE).
     */
    public Flux<String> chatWithStudentStream(String studentId, String userMessage) {
        String cacheKey = studentId + "::" + userMessage;

        String cachedResponse = semanticCacheService.getCachedResponse(cacheKey);
        if (cachedResponse != null) {
            log.debug("Semantic cache HIT for studentId={}", studentId);
            return streamCachedResponse(cachedResponse);
        }

        log.debug("Semantic cache MISS — invoking agent for studentId={}", studentId);

        String systemPrompt = STUDENT_SYSTEM_PROMPT.replace("{studentId}", studentId);

        String fullResponse = agentChatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .tools(STUDENT_TOOLS)
                .advisors(spec -> spec
                        .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, studentId)
                        .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, MEMORY_WINDOW_SIZE))
                .call()
                .content();

        if (fullResponse != null && !fullResponse.isBlank()) {
            semanticCacheService.putCache(cacheKey, fullResponse);
            log.debug("Agent response cached for studentId={}", studentId);
        }

        return streamCachedResponse(fullResponse != null ? fullResponse : "Bir hata oluştu, lütfen tekrar deneyin.");
    }

    /**
     * Replays a cached response with a simulated word-by-word delivery.
     * This keeps the UX consistent whether the response is live or cached.
     */
    private Flux<String> streamCachedResponse(String cachedResponse) {
        return Flux.fromIterable(Arrays.asList(cachedResponse.split("(?<=\\s)")))
                .delayElements(Duration.ofMillis(30));
    }
}