package com.educonnect.common.messaging.dedup;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.ObjectProvider;

public class DuplicateMessageFilter implements MethodInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DuplicateMessageFilter.class);

    private final ObjectProvider<ProcessedMessageStore> store;

    public DuplicateMessageFilter(ObjectProvider<ProcessedMessageStore> store) {
        this.store = store;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Message message = message(invocation.getArguments());
        ProcessedMessageStore current = store.getIfAvailable();
        String queue = message == null ? null : message.getMessageProperties().getConsumerQueue();
        String messageId = message == null ? null : message.getMessageProperties().getMessageId();
        if (current == null || queue == null || messageId == null || messageId.isBlank()) {
            return invocation.proceed();
        }

        if (alreadyProcessed(current, queue, messageId)) {
            log.info("Duplicate message skipped: queue={}, messageId={}", queue, messageId);
            return null;
        }
        Object result = invocation.proceed();
        markProcessed(current, queue, messageId);
        return result;
    }

    private static boolean alreadyProcessed(ProcessedMessageStore store, String queue, String messageId) {
        try {
            return store.isProcessed(queue, messageId);
        } catch (RuntimeException e) {
            log.warn("Duplicate check unavailable, processing message anyway: {}", e.getMessage());
            return false;
        }
    }

    private static void markProcessed(ProcessedMessageStore store, String queue, String messageId) {
        try {
            store.markProcessed(queue, messageId);
        } catch (RuntimeException e) {
            log.warn("Processed message could not be recorded: {}", e.getMessage());
        }
    }

    private static Message message(Object[] arguments) {
        if (arguments != null && arguments.length > 1 && arguments[1] instanceof Message message) {
            return message;
        }
        return null;
    }
}
