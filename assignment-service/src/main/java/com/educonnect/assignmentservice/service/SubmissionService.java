package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.dto.SubmissionVersionResponse;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.model.GroupMember;
import com.educonnect.assignmentservice.model.SubmissionVersion;
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
import com.educonnect.assignmentservice.model.AiPolicy;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class SubmissionService {

    static final int MAX_TEXT_LENGTH = 20000;
    static final int MAX_AI_NOTE_LENGTH = 1000;

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final SubmissionVersionRepository versionRepository;
    private final GroupWork groupWork;
    private final MinioService minioService;
    private final StudentAssignmentCache studentCache;
    private final Clock clock;

    @Autowired
    public SubmissionService(AssignmentRepository assignmentRepository,
                             SubmissionRepository submissionRepository,
                             SubmissionVersionRepository versionRepository,
                             GroupWork groupWork,
                             MinioService minioService,
                             StudentAssignmentCache studentCache) {
        this(assignmentRepository, submissionRepository, versionRepository, groupWork, minioService,
                studentCache, Clock.systemDefaultZone());
    }

    SubmissionService(AssignmentRepository assignmentRepository,
                      SubmissionRepository submissionRepository,
                      SubmissionVersionRepository versionRepository,
                      GroupWork groupWork,
                      MinioService minioService,
                      StudentAssignmentCache studentCache,
                      Clock clock) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.versionRepository = versionRepository;
        this.groupWork = groupWork;
        this.minioService = minioService;
        this.studentCache = studentCache;
        this.clock = clock;
    }

    @CacheEvict(value = AssignmentService.STUDENT_ASSIGNMENTS, key = "#studentId")
    public AssignmentSubmission submit(UUID assignmentId, UUID studentId, MultipartFile file, String text, Boolean aiUsed,
                                       String aiNote) {
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
        if (assignment.getAiPolicy() == AiPolicy.ALLOWED_WITH_DISCLOSURE && aiUsed == null) {
            throw new BadRequestException("AI_DECLARATION_REQUIRED",
                    "Bu ödevde teslimle birlikte yapay zekâ kullanıp kullanmadığınızı beyan etmelisiniz.");
        }
        String note = aiNote == null || aiNote.isBlank() ? null : aiNote.strip();
        if (note != null && note.length() > MAX_AI_NOTE_LENGTH) {
            throw new BadRequestException("AI_NOTE_TOO_LONG", "Yapay zekâ beyanı en fazla " + MAX_AI_NOTE_LENGTH + " karakter olabilir.");
        }
        UUID groupId = null;
        if (assignment.isGroupWork()) {
            groupId = groupWork.membership(assignment, studentId).map(GroupMember::getGroupId)
                    .orElseThrow(() -> new ConflictException("NOT_IN_GROUP", "Bu grup ödevini teslim etmek için bir grupta olmalısınız."));
        }
        DeadlinePolicy.Window window = groupWork.window(assignment, studentId);
        LocalDateTime now = LocalDateTime.now(clock);
        if (window.isClosed(now)) {
            throw new ConflictException("SUBMISSION_CLOSED", "Teslim süresi doldu.");
        }
        boolean late = window.isLate(now);
        Optional<AssignmentSubmission> existing = groupWork.submissionOf(assignment, studentId);
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
        submission.setStudentId(studentId);
        submission.setGroupId(groupId);
        submission.setSubmissionFileUrl(fileUrl);
        submission.setTextContent(body);
        submission.setSubmittedAt(now);
        submission.setLate(late);
        submission.setAiUsed(aiUsed);
        submission.setAiNote(Boolean.TRUE.equals(aiUsed) ? note : null);
        AssignmentSubmission saved = submissionRepository.save(submission);
        versionRepository.save(new SubmissionVersion(saved.getId(), versionRepository.countBySubmissionId(saved.getId()) + 1,
                fileUrl, body, now, late, studentId));
        if (groupId != null) {
            studentCache.evict(groupWork.membersByGroup(List.of(groupId)).getOrDefault(groupId, List.of()));
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<SubmissionVersionResponse> versions(UUID submissionId) {
        return versionRepository.findBySubmissionIdOrderByVersionNoDesc(submissionId).stream()
                .map(v -> new SubmissionVersionResponse(v.getVersionNo(), v.getFileUrl(), v.getTextContent(),
                        v.getSubmittedAt(), v.isLate(), v.getSubmittedBy()))
                .toList();
    }
}
