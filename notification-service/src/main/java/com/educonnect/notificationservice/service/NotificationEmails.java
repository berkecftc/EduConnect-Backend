package com.educonnect.notificationservice.service;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.notificationservice.config.NotificationProperties;
import com.educonnect.notificationservice.listener.HtmlText;
import com.educonnect.notificationservice.model.Notification;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class NotificationEmails {

    private final NotificationProperties properties;
    private final UnsubscribeTokens tokens;

    public NotificationEmails(NotificationProperties properties, UnsubscribeTokens tokens) {
        this.properties = properties;
        this.tokens = tokens;
    }

    public EmailContent generic(Notification notification, String footerHtml) {
        StringBuilder html = new StringBuilder()
                .append("<html><body style=\"font-family: Arial, sans-serif; color: #333;\">")
                .append("<h2 style=\"color: #2c3e50;\">").append(HtmlText.escape(notification.getTitle())).append("</h2>")
                .append("<p>").append(HtmlText.escape(notification.getBody()).replace("\n", "<br>")).append("</p>");
        String link = absoluteLink(notification.getLink());
        if (link != null) {
            html.append("<p><a href=\"").append(HtmlText.escape(link))
                    .append("\" style=\"background:#2c3e50;color:#fff;padding:8px 14px;border-radius:4px;text-decoration:none;\">")
                    .append("EduConnect'te görüntüle</a></p>");
        }
        html.append(footerHtml).append("</body></html>");
        return EmailContent.html(notification.getTitle(), html.toString());
    }

    public String footer(UUID recipientId, NotificationCategory category) {
        String text = category.mandatory()
                ? "Bu bir hizmet bildirimidir; " + HtmlText.escape(category.label()) + " bildirimleri kapatılamaz."
                : HtmlText.escape(category.label()) + " e-postalarını almak istemiyorsanız <a href=\""
                + HtmlText.escape(unsubscribePage(recipientId, category)) + "\">abonelikten çıkın</a>. "
                + "Tercihlerinizi EduConnect bildirim ayarlarından değiştirebilirsiniz.";
        return "<hr style=\"border:0;border-top:1px solid #eee;margin-top:24px;\"><p style=\"font-size:12px;color:#777;\">"
                + text + "</p>";
    }

    public String unsubscribePage(UUID recipientId, NotificationCategory category) {
        return properties.frontendBaseUrl() + "/notifications/unsubscribe?token=" + tokens.issue(recipientId, category);
    }

    public String oneClickUnsubscribe(UUID recipientId, NotificationCategory category) {
        return properties.apiBaseUrl() + "/api/notifications/unsubscribe?token=" + tokens.issue(recipientId, category);
    }

    String absoluteLink(String link) {
        if (link == null || !link.startsWith("/") || link.startsWith("//")) {
            return null;
        }
        return properties.frontendBaseUrl() + link;
    }
}
