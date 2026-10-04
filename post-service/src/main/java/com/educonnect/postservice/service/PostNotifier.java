package com.educonnect.postservice.service;

import com.educonnect.common.messaging.notification.NotificationCategory;
import com.educonnect.common.messaging.notification.NotificationRequest;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.ModerationAction;
import com.educonnect.postservice.model.ModerationActor;
import com.educonnect.postservice.model.ModerationTarget;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class PostNotifier {

    private static final String APPEAL_HINT = "\nBu karara gönderi sayfasından bir kez itiraz edebilirsiniz.";

    private final OutboxPublisher outboxPublisher;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public PostNotifier(OutboxPublisher outboxPublisher, PostRepository postRepository, CommentRepository commentRepository) {
        this.outboxPublisher = outboxPublisher;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    public void notify(Collection<UUID> recipientIds, NotificationCategory category, String type, UUID postId,
                       String title, String body, String dedupKey) {
        if (recipientIds == null) {
            return;
        }
        List<UUID> recipients = recipientIds.stream().filter(Objects::nonNull).distinct().toList();
        if (recipients.isEmpty()) {
            return;
        }
        outboxPublisher.publish(NotificationRequest.EXCHANGE, NotificationRequest.ROUTING_KEY,
                NotificationRequest.of(recipients, category, type, title, body, "/posts/" + postId, dedupKey));
    }

    public void moderationDecision(ModerationTarget target, UUID targetId, UUID postId, ModerationAction action,
                                   ModerationActor actor, String reason) {
        String body = switch (action) {
            case PUBLISHED -> actor == ModerationActor.MODERATOR ? "moderatör incelemesinden geçti ve yayımlandı." : null;
            case REJECTED -> "yayımlanmadı." + reasonLine(reason) + APPEAL_HINT;
            case HIDDEN -> "gizlendi." + reasonLine(reason) + APPEAL_HINT;
            case REMOVED -> "moderatör kararıyla kaldırıldı." + reasonLine(reason) + APPEAL_HINT;
            case RESTORED -> "yeniden yayında." + reasonLine(reason);
            case APPEAL_ACCEPTED -> "için yaptığınız itiraz kabul edildi; içerik yeniden yayında." + reasonLine(reason);
            case APPEAL_REJECTED -> "için yaptığınız itiraz reddedildi; karar kesindir." + reasonLine(reason);
            default -> null;
        };
        if (body == null) {
            return;
        }
        Optional<Post> post = postRepository.findById(postId);
        UUID author = target == ModerationTarget.POST
                ? post.map(Post::getAuthorId).orElse(null)
                : commentRepository.findById(targetId).map(Comment::getAuthorId).orElse(null);
        String subject = describe(target, post.orElse(null));
        notify(Optional.ofNullable(author).map(List::of).orElse(List.of()), NotificationCategory.MODERATION,
                "MODERATION_" + action.name(), postId, title(action, target), capitalize(subject) + " " + body, null);
    }

    public void reportsResolved(Collection<UUID> reporters, ModerationTarget target, UUID postId, boolean upheld, String note) {
        String subject = target == ModerationTarget.POST ? "şikâyet ettiğiniz gönderi" : "şikâyet ettiğiniz yorum";
        notify(reporters, NotificationCategory.MODERATION, "REPORT_RESOLVED", postId, "Şikâyetiniz sonuçlandı",
                upheld
                        ? "İncelemeniz için teşekkürler; " + subject + " topluluk kurallarına aykırı bulunarak kaldırıldı."
                        : "Moderatör, " + subject + " için kural ihlali bulmadı; içerik yayında kalıyor." + reasonLine(note),
                null);
    }

    public void commentPublished(Comment comment) {
        Post post = postRepository.findById(comment.getPostId()).orElse(null);
        if (post == null) {
            return;
        }
        UUID commenter = comment.getAuthorId();
        UUID parentAuthor = comment.getParentCommentId() == null ? null
                : commentRepository.findById(comment.getParentCommentId()).map(Comment::getAuthorId).orElse(null);
        String dedupKey = "comment:" + comment.getId();
        List<UUID> repliedTo = new ArrayList<>();
        if (parentAuthor != null && !parentAuthor.equals(commenter)) {
            repliedTo.add(parentAuthor);
            notify(repliedTo, NotificationCategory.COMMUNITY, "COMMENT_REPLY", post.getId(), "Yorumunuza yanıt geldi",
                    "\"" + post.getTitle() + "\" gönderisindeki yorumunuza yeni bir yanıt var.", dedupKey);
        }
        UUID postAuthor = post.getAuthorId();
        if (postAuthor != null && !postAuthor.equals(commenter) && !repliedTo.contains(postAuthor)) {
            notify(List.of(postAuthor), NotificationCategory.COMMUNITY, "POST_COMMENT", post.getId(),
                    "Gönderinize yeni yorum", "\"" + post.getTitle() + "\" gönderinize yeni bir yorum yapıldı.", dedupKey);
        }
    }

    private static String title(ModerationAction action, ModerationTarget target) {
        String content = target == ModerationTarget.POST ? "Gönderiniz" : "Yorumunuz";
        return switch (action) {
            case PUBLISHED -> content + " onaylandı";
            case REJECTED -> content + " yayımlanmadı";
            case HIDDEN -> content + " gizlendi";
            case REMOVED -> content + " kaldırıldı";
            case RESTORED -> content + " yeniden yayında";
            case APPEAL_ACCEPTED -> "İtirazınız kabul edildi";
            case APPEAL_REJECTED -> "İtirazınız reddedildi";
            default -> content;
        };
    }

    private static String describe(ModerationTarget target, Post post) {
        String postTitle = post != null ? "\"" + post.getTitle() + "\"" : "bir";
        return target == ModerationTarget.POST ? postTitle + " gönderiniz" : postTitle + " gönderisindeki yorumunuz";
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static String reasonLine(String reason) {
        return reason == null || reason.isBlank() ? "" : "\nGerekçe: " + reason.strip();
    }
}
