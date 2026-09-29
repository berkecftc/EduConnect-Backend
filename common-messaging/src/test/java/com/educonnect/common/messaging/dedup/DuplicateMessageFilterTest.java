package com.educonnect.common.messaging.dedup;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DuplicateMessageFilterTest {

    private final InMemoryStore store = new InMemoryStore();

    @Test
    void firstDelivery_shouldProcessAndRecord() throws Throwable {
        MethodInvocation invocation = invocation(message("q", "m-1"));

        filter(store).invoke(invocation);

        verify(invocation).proceed();
        assertThat(store.ids).containsExactly("q:m-1");
    }

    @Test
    void redelivery_shouldBeSkipped() throws Throwable {
        store.markProcessed("q", "m-1");
        MethodInvocation invocation = invocation(message("q", "m-1"));

        assertThat(filter(store).invoke(invocation)).isNull();

        verify(invocation, never()).proceed();
    }

    @Test
    void failedProcessing_shouldNotBeRecordedSoRetryCanRun() throws Throwable {
        MethodInvocation invocation = invocation(message("q", "m-1"));
        when(invocation.proceed()).thenThrow(new IllegalStateException("mail server down"));

        assertThatThrownBy(() -> filter(store).invoke(invocation)).isInstanceOf(IllegalStateException.class);

        assertThat(store.ids).isEmpty();
    }

    @Test
    void messageWithoutId_shouldBeProcessedWithoutRecording() throws Throwable {
        MethodInvocation invocation = invocation(message("q", null));

        filter(store).invoke(invocation);

        verify(invocation).proceed();
        assertThat(store.ids).isEmpty();
    }

    @Test
    void storeUnavailable_shouldFailOpen() throws Throwable {
        ProcessedMessageStore broken = mock(ProcessedMessageStore.class);
        when(broken.isProcessed("q", "m-1")).thenThrow(new RuntimeException("redis down"));
        MethodInvocation invocation = invocation(message("q", "m-1"));

        filter(broken).invoke(invocation);

        verify(invocation, times(1)).proceed();
    }

    @Test
    void noStoreConfigured_shouldProcessNormally() throws Throwable {
        MethodInvocation invocation = invocation(message("q", "m-1"));

        filter(null).invoke(invocation);

        verify(invocation).proceed();
    }

    @SuppressWarnings("unchecked")
    private static DuplicateMessageFilter filter(ProcessedMessageStore store) {
        ObjectProvider<ProcessedMessageStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        return new DuplicateMessageFilter(provider);
    }

    private static MethodInvocation invocation(Message message) {
        MethodInvocation invocation = mock(MethodInvocation.class);
        when(invocation.getArguments()).thenReturn(new Object[]{null, message});
        return invocation;
    }

    private static Message message(String queue, String messageId) {
        MessageProperties properties = new MessageProperties();
        properties.setConsumerQueue(queue);
        properties.setMessageId(messageId);
        return new Message(new byte[0], properties);
    }

    private static final class InMemoryStore implements ProcessedMessageStore {
        private final Set<String> ids = new HashSet<>();

        @Override
        public boolean isProcessed(String queue, String messageId) {
            return ids.contains(queue + ":" + messageId);
        }

        @Override
        public void markProcessed(String queue, String messageId) {
            ids.add(queue + ":" + messageId);
        }
    }
}
