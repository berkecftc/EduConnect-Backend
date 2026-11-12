package com.educonnect.notificationservice.service;

public record EmailContent(String subject, String html, InlineImage inlineImage) {

    public record InlineImage(String contentId, byte[] data, String contentType) {
    }

    public static EmailContent html(String subject, String html) {
        return new EmailContent(subject, html, null);
    }
}
