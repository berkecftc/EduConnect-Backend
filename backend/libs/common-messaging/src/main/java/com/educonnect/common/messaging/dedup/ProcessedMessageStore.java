package com.educonnect.common.messaging.dedup;

public interface ProcessedMessageStore {

    boolean isProcessed(String queue, String messageId);

    void markProcessed(String queue, String messageId);
}
