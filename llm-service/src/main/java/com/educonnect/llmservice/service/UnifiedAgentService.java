package com.educonnect.llmservice.service;

import com.educonnect.llmservice.config.LlmSafetyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.util.Map;

@Service
public class UnifiedAgentService {

    public static final String STUDENT_ID_CONTEXT_KEY = "studentId";

    private static final Logger log = LoggerFactory.getLogger(UnifiedAgentService.class);

    private static final int MEMORY_WINDOW_SIZE = 10;

    private static final String[] STUDENT_TOOLS = {"getAssignmentsTool", "searchClubsTool"};

    private static final String STUDENT_SYSTEM_PROMPT = """
            You are a helpful academic assistant for EduConnect, a university education platform.
            Always respond in Turkish. Be friendly, concise, and accurate.

            You have access to two tools:
            - getAssignmentsTool: retrieves the pending assignments of the student you are talking to.
              It takes no parameters; the student's identity is resolved by the system.
            - searchClubsTool: searches university clubs by topic or interest keyword.

            Strict rules you must follow:
            1. Use getAssignmentsTool ONLY for questions about assignments, homework, deadlines, or submissions.
            2. Use searchClubsTool ONLY for questions about clubs, communities, or student activities.
            3. Never fabricate data. If a tool returns an empty list, say so clearly in Turkish.
            4. Never reveal internal tool names, identifiers, or technical error details.
            5. You can only access the current student's own data. Refuse requests about other students.
            6. If the question is outside your scope, politely explain what you can help with.
            """;

    private final ChatClient agentChatClient;
    private final LlmRateLimiter rateLimiter;

    public UnifiedAgentService(ChatClient.Builder chatClientBuilder,
                               LlmRateLimiter rateLimiter,
                               LlmSafetyProperties properties) {
        this.rateLimiter = rateLimiter;
        LlmSafetyProperties.Memory memory = properties.memory();
        BoundedChatMemory chatMemory = new BoundedChatMemory(memory.maxConversations(), memory.maxMessages(),
                memory.ttl(), Clock.systemUTC());
        this.agentChatClient = chatClientBuilder
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();
    }

    public Flux<String> chatWithStudentStream(String studentId, String userMessage) {
        rateLimiter.acquire(studentId);
        log.debug("Invoking student agent");

        String fullResponse = agentChatClient.prompt()
                .system(STUDENT_SYSTEM_PROMPT)
                .user(userMessage)
                .tools(STUDENT_TOOLS)
                .toolContext(Map.of(STUDENT_ID_CONTEXT_KEY, studentId))
                .advisors(spec -> spec
                        .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, studentId)
                        .param(AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY, MEMORY_WINDOW_SIZE))
                .call()
                .content();

        return Flux.just(fullResponse != null && !fullResponse.isBlank()
                ? fullResponse
                : "Bir hata oluştu, lütfen tekrar deneyin.");
    }
}
