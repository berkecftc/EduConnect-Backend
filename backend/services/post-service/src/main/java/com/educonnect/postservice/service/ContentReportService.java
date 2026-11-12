package com.educonnect.postservice.service;

import com.educonnect.common.web.ApiException;
import com.educonnect.postservice.dto.ModerationReportItem;
import com.educonnect.postservice.dto.ReportRequest;
import com.educonnect.postservice.dto.ReportResponse;
import com.educonnect.postservice.exception.CommentNotFoundException;
import com.educonnect.postservice.model.Comment;
import com.educonnect.postservice.model.CommentStatus;
import com.educonnect.postservice.model.ContentReport;
import com.educonnect.postservice.model.ModerationAction;
import com.educonnect.postservice.model.ModerationActor;
import com.educonnect.postservice.model.ModerationTarget;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.ContentReportRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.Collections;

@Service
public class ContentReportService {

    private static final Logger log = LoggerFactory.getLogger(ContentReportService.class);

    private final ContentReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostVisibility postVisibility;
    private final ContentControlService contentControl;
    private final ModerationLog moderationLog;
    private final int hideThreshold;
    private final PostNotifier notifier;

    public ContentReportService(ContentReportRepository reportRepository,
                                PostRepository postRepository,
                                CommentRepository commentRepository,
                                PostVisibility postVisibility,
                                ContentControlService contentControl,
                                ModerationLog moderationLog,
                                @Value("${educonnect.post.moderation.report-hide-threshold:3}") int hideThreshold,
                                PostNotifier notifier) {
        this.reportRepository = reportRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postVisibility = postVisibility;
        this.contentControl = contentControl;
        this.moderationLog = moderationLog;
        this.hideThreshold = hideThreshold;
        this.notifier = notifier;
    }

    @Transactional
    public ReportResponse reportPost(UUID postId, ReportRequest request, Viewer viewer) {
        Post post = postVisibility.requirePublished(postId, viewer, "Yalnız yayındaki içerik şikâyet edilebilir.");
        return report(ModerationTarget.POST, post.getId(), post.getId(), post.getAuthorId(), request, viewer);
    }

    @Transactional
    public ReportResponse reportComment(UUID commentId, ReportRequest request, Viewer viewer) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException("Yorum bulunamadı: " + commentId));
        postVisibility.requireVisible(comment.getPostId(), viewer);
        if (comment.getStatus() != CommentStatus.PUBLISHED) {
            throw new IllegalArgumentException("Yalnız yayındaki içerik şikâyet edilebilir.");
        }
        return report(ModerationTarget.COMMENT, comment.getId(), comment.getPostId(), comment.getAuthorId(), request, viewer);
    }

    @Transactional(readOnly = true)
    public Page<ModerationReportItem> openReports(Viewer moderator, Pageable pageable) {
        moderator.requireModerator();
        return reportRepository.findByStatusSensitiveFirst(ContentReport.Status.OPEN, pageable).map(this::toItem);
    }

    @Transactional
    public void dismiss(UUID reportId, String note, Viewer moderator) {
        moderator.requireModerator();
        ContentReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "Şikâyet bulunamadı."));
        if (report.getStatus() != ContentReport.Status.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "REPORT_CLOSED", "Bu şikâyet zaten sonuçlandı.");
        }
        report.resolve(ContentReport.Status.DISMISSED, moderator.id(), note.strip(), Instant.now());
        reportRepository.save(report);
        moderationLog.record(report.getTargetType(), report.getTargetId(), report.getPostId(), ModerationAction.REPORT_DISMISSED,
                ModerationActor.MODERATOR, moderator.id(), note.strip());
        notifier.reportsResolved(Collections.singletonList(report.getReporterId()), report.getTargetType(), report.getPostId(),
                false, note.strip());
    }

    private ReportResponse report(ModerationTarget target, UUID targetId, UUID postId, UUID authorId,
                                  ReportRequest request, Viewer viewer) {
        if (viewer.id().equals(authorId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_REPORT_OWN", "Kendi içeriğinizi şikâyet edemezsiniz.");
        }
        if (reportRepository.existsByTargetTypeAndTargetIdAndReporterIdAndStatus(target, targetId, viewer.id(),
                ContentReport.Status.OPEN)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_REPORTED", "Bu içeriği zaten şikâyet ettiniz.");
        }
        String details = request.details() == null || request.details().isBlank() ? null : request.details().strip();
        ContentReport report = reportRepository.save(new ContentReport(target, targetId, postId, viewer.id(),
                request.reason(), details, Instant.now()));
        long open = reportRepository.countByTargetTypeAndTargetIdAndStatus(target, targetId, ContentReport.Status.OPEN);
        log.info("Content reported. target={}, targetId={}, reason={}, openReports={}", target, targetId, request.reason(), open);
        if (open >= hideThreshold) {
            contentControl.hideForReports(target, targetId);
        }
        return ReportResponse.from(report);
    }

    private ModerationReportItem toItem(ContentReport report) {
        String status;
        String content;
        if (report.getTargetType() == ModerationTarget.POST) {
            Post post = postRepository.findById(report.getTargetId()).orElse(null);
            status = post != null ? post.getStatus().name() : null;
            content = post != null ? post.getTitle() + "\n" + post.getContent() : null;
        } else {
            Comment comment = commentRepository.findById(report.getTargetId()).orElse(null);
            status = comment != null ? comment.getStatus().name() : null;
            content = comment != null ? comment.getContent() : null;
        }
        long open = reportRepository.countByTargetTypeAndTargetIdAndStatus(report.getTargetType(), report.getTargetId(),
                ContentReport.Status.OPEN);
        return new ModerationReportItem(report.getId(), report.getTargetType(), report.getTargetId(), report.getPostId(),
                report.getReason(), report.getReason().label(), report.getReason().sensitive(), report.getDetails(),
                report.getReporterId(), report.getStatus(), open, status, content, report.getCreatedAt());
    }
}
