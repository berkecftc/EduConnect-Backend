package com.educonnect.common.messaging;

public final class DeadLetters {

    public static final String EXCHANGE = "educonnect.dlx";
    public static final String QUEUE_SUFFIX = ".dlq";

    public static final String HEADER_EXCEPTION_CLASS = "x-exception-class";
    public static final String HEADER_EXCEPTION_MESSAGE = "x-exception-message";
    public static final String HEADER_ORIGINAL_QUEUE = "x-original-queue";
    public static final String HEADER_ORIGINAL_EXCHANGE = "x-original-exchange";
    public static final String HEADER_ORIGINAL_ROUTING_KEY = "x-original-routing-key";
    public static final String HEADER_FAILED_AT = "x-failed-at";

    private DeadLetters() {
    }

    public static String queueFor(String queue) {
        return queue + QUEUE_SUFFIX;
    }
}
