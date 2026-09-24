package com.educonnect.llmservice.service;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BoundedChatMemory implements ChatMemory {

    private final int maxConversations;
    private final int maxMessagesPerConversation;
    private final Duration ttl;
    private final Clock clock;
    private final LinkedHashMap<String, Conversation> conversations = new LinkedHashMap<>(16, 0.75f, true);

    public BoundedChatMemory(int maxConversations, int maxMessagesPerConversation, Duration ttl, Clock clock) {
        this.maxConversations = maxConversations;
        this.maxMessagesPerConversation = maxMessagesPerConversation;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    public synchronized void add(String conversationId, List<Message> messages) {
        Conversation conversation = activeConversation(conversationId);
        if (conversation == null) {
            conversation = new Conversation();
            conversations.put(conversationId, conversation);
        }
        conversation.messages.addAll(messages);
        int overflow = conversation.messages.size() - maxMessagesPerConversation;
        if (overflow > 0) {
            conversation.messages.subList(0, overflow).clear();
        }
        conversation.lastAccess = clock.millis();
        evictOverflow();
    }

    @Override
    public synchronized List<Message> get(String conversationId, int lastN) {
        Conversation conversation = activeConversation(conversationId);
        if (conversation == null) {
            return List.of();
        }
        conversation.lastAccess = clock.millis();
        List<Message> messages = conversation.messages;
        int from = Math.max(0, messages.size() - lastN);
        return List.copyOf(messages.subList(from, messages.size()));
    }

    @Override
    public synchronized void clear(String conversationId) {
        conversations.remove(conversationId);
    }

    synchronized int size() {
        return conversations.size();
    }

    private Conversation activeConversation(String conversationId) {
        Conversation conversation = conversations.get(conversationId);
        if (conversation != null && clock.millis() - conversation.lastAccess > ttl.toMillis()) {
            conversations.remove(conversationId);
            return null;
        }
        return conversation;
    }

    private void evictOverflow() {
        long now = clock.millis();
        conversations.entrySet().removeIf(entry -> now - entry.getValue().lastAccess > ttl.toMillis());
        while (conversations.size() > maxConversations) {
            Map.Entry<String, Conversation> eldest = conversations.entrySet().iterator().next();
            conversations.remove(eldest.getKey());
        }
    }

    private static final class Conversation {
        private final List<Message> messages = new ArrayList<>();
        private long lastAccess;
    }
}
