package org.springframework.ai.chat.client;

/**
 * Minimal prompt/response stubs to satisfy the usage in CopilotService.
 */
public class Prompt {
    private final String systemContext;
    private String userMessage = "";
    private String functions = "";

    public Prompt(String systemContext) {
        this.systemContext = systemContext;
    }

    public Prompt user(String message) {
        this.userMessage = message;
        return this;
    }

    public Prompt functions(String functionsName) {
        this.functions = functionsName;
        return this;
    }

    public Response call() {
        throw new UnsupportedOperationException(
                "Local Spring AI stub is disabled. Configure a real LLM provider before calling prompt()."
        );
    }

    public static class Response {
        private final String content;

        public Response(String content) {
            this.content = content;
        }

        public String content() {
            return content;
        }
    }
}

