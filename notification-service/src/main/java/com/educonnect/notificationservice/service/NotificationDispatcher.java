package com.educonnect.notificationservice.service;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.NotificationRequest;
import com.educonnect.notificationservice.client.ContactDirectory;
import com.educonnect.notificationservice.model.EmailStatus;
import com.educonnect.notificationservice.model.Notification;
import com.educonnect.notificationservice.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);
    private static final int MAX_TITLE = 255;
    private static final int MAX_LINK = 500;

    private final NotificationRepository repository;
    private final ContactDirectory contacts;
    private final PreferenceService preferences;
    private final NotificationEmails emails;
    private final EmailService emailService;
    private final TransactionTemplate transactions;

    public NotificationDispatcher(NotificationRepository repository,
                                  ContactDirectory contacts,
                                  PreferenceService preferences,
                                  NotificationEmails emails,
                                  EmailService emailService,
                                  PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.contacts = contacts;
        this.preferences = preferences;
        this.emails = emails;
        this.emailService = emailService;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public int dispatch(NotificationRequest request) {
        return dispatch(request, null);
    }

    public int dispatch(NotificationRequest request, EmailComposer composer) {
        validate(request);
        Set<UUID> recipients = new LinkedHashSet<>(request.recipientIds().stream().filter(Objects::nonNull).toList());
        if (recipients.isEmpty()) {
            return 0;
        }
        Map<UUID, ContactDirectory.Contact> directory = contacts.lookup(recipients);
        List<Notification> stored = transactions.execute(status -> store(request, recipients));
        if (stored == null || stored.isEmpty()) {
            return 0;
        }
        Map<UUID, Boolean> emailAllowed = preferences.emailEnabled(recipients, request.category());
        for (Notification notification : stored) {
            notification.emailStatus(sendEmail(notification, directory.get(notification.getRecipientId()),
                    Boolean.TRUE.equals(emailAllowed.get(notification.getRecipientId())), composer));
        }
        repository.saveAll(stored);
        log.info("Notification {} ({}) delivered to {} recipient(s)", request.type(), request.category(), stored.size());
        return stored.size();
    }

    private List<Notification> store(NotificationRequest request, Set<UUID> recipients) {
        Instant now = Instant.now();
        List<Notification> stored = new ArrayList<>();
        String title = truncate(request.title(), MAX_TITLE);
        String link = request.link() != null && request.link().length() <= MAX_LINK ? request.link() : null;
        for (UUID recipient : recipients) {
            if (request.dedupKey() != null && repository.existsByRecipientIdAndDedupKey(recipient, request.dedupKey())) {
                continue;
            }
            stored.add(new Notification(recipient, request.category(), request.type(), title, request.body(), link,
                    request.dedupKey(), now));
        }
        return repository.saveAll(stored);
    }

    private EmailStatus sendEmail(Notification notification, ContactDirectory.Contact contact, boolean allowed,
                                  EmailComposer composer) {
        if (!allowed || contact == null || !contact.active() || contact.email() == null) {
            return EmailStatus.SKIPPED;
        }
        NotificationCategory category = notification.getCategory();
        String footer = emails.footer(notification.getRecipientId(), category);
        EmailContent content = composer != null ? composer.compose(notification, footer) : emails.generic(notification, footer);
        String oneClick = category.mandatory() ? null : emails.oneClickUnsubscribe(notification.getRecipientId(), category);
        return emailService.sendNotification(contact.email(), content, oneClick) ? EmailStatus.SENT : EmailStatus.FAILED;
    }

    private static void validate(NotificationRequest request) {
        if (request == null || request.recipientIds() == null || request.category() == null
                || isBlank(request.type()) || isBlank(request.title()) || request.body() == null) {
            throw new AmqpRejectAndDontRequeueException("Invalid notification request");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
