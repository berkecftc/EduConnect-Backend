package org.springframework.ai.chat.client;

/**
 * Minimal local stub of ChatClient used so the project compiles when the real
 * Spring AI dependency is not present. Replace with the official
 * org.springframework.ai:... dependency in production.
 */
public class ChatClient {

    private final String systemPrompt;

    public ChatClient(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public Prompt prompt() {
        return new Prompt(systemPrompt);
    }

    public static class Builder {
        private String defaultSystemPrompt = "";

        public Builder defaultSystem(String systemPrompt) {
            this.defaultSystemPrompt = systemPrompt;
            return this;
        }

        public ChatClient build() {
            return new ChatClient(defaultSystemPrompt);
        }
    }
}

