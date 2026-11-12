package com.educonnect.notificationservice.service;

import com.educonnect.notificationservice.model.Notification;

@FunctionalInterface
public interface EmailComposer {

    EmailContent compose(Notification notification, String footerHtml);
}
