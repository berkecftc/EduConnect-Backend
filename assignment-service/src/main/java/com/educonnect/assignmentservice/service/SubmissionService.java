package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.dto.SubmissionVersionResponse;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.model.SubmissionVersion;
import com.educonnect.assignmentservice.repository.AssignmentExtensionRepository;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.assignmentservice.repository.SubmissionVersionRepository;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SubmissionService {

    static final int MAX_TEXT_LENGTH = 20000;

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final AssignmentExtensionRepository extensionRepository;
    private final MinioService minioService;
    private final Clock clock;

    @Autowired
    public SubmissionService(AssignmentRepository assignmentRepository,
                             SubmissionRepository submissionRepository,
                             SubmissionVersionRepository versionRepository,
                             AssignmentExtensionRepository extensionRepository,
                             MinioService minioService) {
        this(assignmentRepository, submissionRepository, versionRepository, extensionRepository, minioService,
                Clock.systemDefaultZone());
    }

    SubmissionService(AssignmentRepository assignmentRepository,
                      SubmissionRepository submissionRepository,
                      SubmissionVersionRepository versionRepository,
                      AssignmentExtensionRepository extensionRepository,
                      MinioService minioService,
                      Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
        this.extensionRepository = extensionRepository;
        this.minioService = minioService;
        this.clock = clock;
    }

    @CacheEvict(value = AssignmentService.STUDENT_ASSIGNMENTS, key = "#studentId")
    public AssignmentSubmission submit(UUID assignmentId, UUID studentId, MultipartFile file, String text) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND", "Ödev bulunamadı"));
        String body = text == null || text.isBlank() ? null : text.strip();
        boolean hasFile = file != null && !file.isEmpty();
        if (!hasFile && body == null) {
            throw new BadRequestException("SUBMISSION_EMPTY", "Teslim için dosya veya metin eklenmeli.");
        }
        if (body != null && body.length() > MAX_TEXT_LENGTH) {
            throw new BadRequestException("SUBMISSION_TOO_LONG", "Metin en fazla " + MAX_TEXT_LENGTH + " karakter olabilir.");
        }
        DeadlinePolicy.Window window = DeadlinePolicy.window(assignment,
                extensionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId).orElse(null));
        LocalDateTime now = LocalDateTime.now(clock);
        if (window.isClosed(now)) {
            throw new ConflictException("SUBMISSION_CLOSED", "Teslim süresi doldu.");
        }
        boolean late = window.isLate(now);
        Optional<AssignmentSubmission> existing = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, studentId);
        existing.ifPresent(previous -> {
            if (previous.getGrade() != null) {
                throw new ConflictException("SUBMISSION_GRADED", "Notlanmış bir teslim değiştirilemez.");
            }
            if (late) {
                throw new ConflictException("RESUBMISSION_CLOSED", "Son teslim tarihi geçtikten sonra teslim değiştirilemez.");
            }
        });
        String fileUrl = hasFile ? minioService.normalizeToFullUrl(minioService.uploadFile(file)) : null;
        AssignmentSubmission submission = existing.orElseGet(() -> new AssignmentSubmission(assignmentId, studentId, null, late));
        submission.setSubmissionFileUrl(fileUrl);
        submission.setTextContent(body);
        submission.setSubmittedAt(now);
        submission.setLate(late);
        AssignmentSubmission saved = submissionRepository.save(submission);
        versionRepository.save(new SubmissionVersion(saved.getId(), versionRepository.countBySubmissionId(saved.getId()) + 1,
                fileUrl, body, now, late));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SubmissionVersionResponse> versions(UUID submissionId) {
        return versionRepository.findBySubmissionIdOrderByVersionNoDesc(submissionId).stream()
                .map(v -> new SubmissionVersionResponse(v.getVersionNo(), v.getFileUrl(), v.getTextContent(),
                        v.getSubmittedAt(), v.isLate()))
                .toList();
    }
}
