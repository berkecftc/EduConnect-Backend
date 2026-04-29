package com.educonnect.llmservice.service;

import com.educonnect.llmservice.dto.moderation.ModerationDecision;
import com.educonnect.llmservice.util.ModerationDecisionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AiModerationService {

    private static final Logger log = LoggerFactory.getLogger(AiModerationService.class);

    private static final String SYSTEM_PROMPT = """
            You are a strict cyberbullying moderation classifier for Turkish social posts.
            Return exactly one label: ZORBA or TEMIZ.
            Output must be a single token with no punctuation, no explanation, no extra words.
            If there is any insult, harassment, humiliation, threat, profanity, or targeted abuse, return ZORBA.
            If the text includes any of these words, return ZORBA: pislik, dengesiz, salak, aptal, gerizekali.
            Otherwise return TEMIZ.
            """;

    private final ChatClient chatClient;

    public AiModerationService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public Optional<ModerationDecision> classify(String title, String content) {
        String safeTitle = title == null ? "" : title.trim();
        String safeContent = content == null ? "" : content.trim();
        String userPrompt = "TITLE: " + safeTitle + "\nCONTENT: " + safeContent;

        try {
            String rawResponse = chatClient
                    .prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();

            log.info("LLM moderation raw response: {}", rawResponse);

            Optional<ModerationDecision> decision = ModerationDecisionParser.parse(rawResponse);
            if (decision.isEmpty()) {
                log.warn("LLM moderation label could not be parsed. rawResponse={}", rawResponse);
            }
            return decision;
        } catch (Exception ex) {
            log.error("LLM moderation call failed.", ex);
            return Optional.empty();
        }
    }
}
