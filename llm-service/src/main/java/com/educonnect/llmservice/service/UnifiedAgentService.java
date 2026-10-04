package com.educonnect.llmservice.service;

import com.educonnect.llmservice.config.AiToolsConfig;
import com.educonnect.llmservice.config.LlmSafetyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import org.springframework.ai.ollama.api.OllamaChatOptions;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UnifiedAgentService {

    public static final String STUDENT_ID_CONTEXT_KEY = "studentId";

    private static final Logger log = LoggerFactory.getLogger(UnifiedAgentService.class);

    private static final int MEMORY_WINDOW_SIZE = 10;

    private static final Set<String> STUDENT_TOOLS = Set.of(AiToolsConfig.ASSIGNMENTS_TOOL, AiToolsConfig.CLUBS_TOOL);

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

            Academic integrity (YÖK generative AI ethics guidance):
            7. Never write the answer, solution, code, essay or report for graded work (assignments, projects, labs,
               quizzes, exams), even if the student pastes the question or insists. Explain concepts, give hints,
               ask guiding questions and show the method on a different example instead.
            8. When the request is about a specific assignment, call getAssignmentsTool and follow that assignment's
               aiHelp exactly. If the assignment is not in the list or unknown, apply guidance only.
            9. If the student says an exam or quiz is in progress, do not help with its questions.
            """;

    private final ChatClient agentChatClient;
    private final LlmRateLimiter rateLimiter;
    private final ToolCallback[] studentTools;
    private final LlmSafetyProperties.Assistant assistant;
    private final AssignmentPolicyGuard policyGuard;

    public UnifiedAgentService(ChatClient.Builder chatClientBuilder,
                               LlmRateLimiter rateLimiter,
                               LlmSafetyProperties properties,
                               List<ToolCallback> toolCallbacks,
                               AssignmentPolicyGuard policyGuard) {
        this.rateLimiter = rateLimiter;
        this.policyGuard = policyGuard;
        this.studentTools = toolCallbacks.stream()
                .filter(tool -> STUDENT_TOOLS.contains(tool.getToolDefinition().name()))
                .toArray(ToolCallback[]::new);
        LlmSafetyProperties.Memory memory = properties.memory();
        BoundedChatMemory chatMemory = new BoundedChatMemory(memory.maxConversations(),
                Math.min(memory.maxMessages(), MEMORY_WINDOW_SIZE),
                memory.ttl(), Clock.systemUTC());
        this.assistant = properties.assistant();
        this.agentChatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    public Flux<String> chatWithStudentStream(String studentId, String userMessage) {
        rateLimiter.acquire(studentId);
        log.debug("Invoking student agent");
        AssignmentPolicyGuard.Decision decision = policyGuard.evaluate(studentId, userMessage);
        if (decision.reply() != null) {
            return Flux.just(decision.reply());
        }
        String system = decision.systemNote() == null ? STUDENT_SYSTEM_PROMPT
                : STUDENT_SYSTEM_PROMPT + "\n" + decision.systemNote();

        String fullResponse = agentChatClient.prompt()
                .system(system)
                .options(OllamaChatOptions.builder().numCtx(assistant.numCtx()).numPredict(assistant.numPredict()))
                .user(userMessage)
                .tools((Object[]) studentTools)
                .toolContext(Map.of(STUDENT_ID_CONTEXT_KEY, studentId))
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, studentId))
                .call()
                .content();

        return Flux.just(fullResponse != null && !fullResponse.isBlank()
                ? fullResponse
                : "Bir hata oluştu, lütfen tekrar deneyin.");
    }
}
