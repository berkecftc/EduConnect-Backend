package com.educonnect.assignmentservice.service;

import com.educonnect.common.web.LogValues;
import com.educonnect.assignmentservice.client.CourseClient;
import com.educonnect.assignmentservice.client.CourseInternalClient;
import com.educonnect.assignmentservice.client.UserClient;
import com.educonnect.assignmentservice.dto.*;
import com.educonnect.assignmentservice.event.AssignmentNotificationEvent;
import com.educonnect.assignmentservice.model.AssessmentType;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentExtension;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.model.CourseGroup;
import com.educonnect.assignmentservice.model.GroupMember;
import com.educonnect.assignmentservice.publisher.AssignmentProducer;
import com.educonnect.assignmentservice.repository.AssignmentExtensionRepository;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.CourseGroupRepository;
import com.educonnect.assignmentservice.repository.GroupMemberRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import com.educonnect.assignmentservice.model.AiPolicy;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    public static final String STUDENT_ASSIGNMENTS = "studentAssignments";

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final MinioService minioService;
    private final CourseClient courseClient;
    private final CourseInternalClient courseInternalClient;
    private final UserClient userClient;
    private final AssignmentProducer assignmentProducer;
    private final StudentDirectory studentDirectory;
    private final AssignmentFiles assignmentFiles;
    private final AssessmentRules assessmentRules;
    private final AssignmentExtensionRepository extensionRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final CourseGroupRepository groupRepository;
    private final GroupWork groupWork;

    public AssignmentService(AssignmentRepository repo, SubmissionRepository subRepo,
                             MinioService minio, CourseClient client, CourseInternalClient internalClient,
                             UserClient userClient, AssignmentProducer producer,
                             StudentDirectory studentDirectory,
                             AssignmentFiles assignmentFiles, AssessmentRules assessmentRules,
                             AssignmentExtensionRepository extensionRepository,
                             GroupMemberRepository groupMemberRepository, CourseGroupRepository groupRepository,
                             GroupWork groupWork) {
        this.studentDirectory = studentDirectory;
        this.assignmentRepository = repo;
        this.submissionRepository = subRepo;
        this.minioService = minio;
        this.courseClient = client;
        this.courseInternalClient = internalClient;
        this.userClient = userClient;
        this.assignmentProducer = producer;
        this.assignmentFiles = assignmentFiles;
        this.assessmentRules = assessmentRules;
        this.extensionRepository = extensionRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupRepository = groupRepository;
        this.groupWork = groupWork;
    }

    @CacheEvict(value = STUDENT_ASSIGNMENTS, allEntries = true)
    public AssignmentResponse createAssignment(AssignmentRequest request, MultipartFile file) {
        if (request.getDueDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Son teslim tarihi zorunludur.");
        }
        assessmentRules.requireWeightFits(request.getCourseId(), null, request.getWeight());
        AssessmentRules.requireLateWindow(request.getDueDate(), request.getLateUntil());
        assessmentRules.requireGroupSet(request.getCourseId(), request.getGroupSetId());
        // 1. Önce böyle bir ders var mı diye Course Service'e sor
        Map<String, Object> courseData;
        try {
            courseData = courseClient.getCourseById(request.getCourseId());
        } catch (Exception e) {
            throw new BadRequestException("COURSE_NOT_FOUND", "Ders bulunamadı! Geçersiz Course ID.");
        }

        // 2. Dosya yükle (varsa)
        String fileUrl = null;
        if (file != null && !file.isEmpty()) {
            fileUrl = minioService.uploadFile(file);
        }

        // 3. Kaydet
        Assignment assignment = new Assignment();
        assignment.setTitle(request.getTitle());
        assignment.setDescription(request.getDescription());
        assignment.setDueDate(request.getDueDate());
        assignment.setCourseId(request.getCourseId());
        assignment.setFileUrl(minioService.normalizeToFullUrl(fileUrl));
        assignment.setType(request.getType() != null ? request.getType() : AssessmentType.HOMEWORK);
        assignment.setWeight(request.getWeight() != null ? request.getWeight() : BigDecimal.ZERO);
        assignment.setMaxPoints(request.getMaxPoints() != null ? request.getMaxPoints() : BigDecimal.valueOf(100));
        assignment.setLateUntil(request.getLateUntil());
        assignment.setGroupSetId(request.getGroupSetId());
        assignment.setLatePenaltyPercent(request.getLatePenaltyPercent() != null ? request.getLatePenaltyPercent() : BigDecimal.ZERO);
        assignment.setAiPolicy(request.getAiPolicy() != null ? request.getAiPolicy() : AiPolicy.GUIDANCE);

        Assignment saved = assignmentRepository.save(assignment);

        // 4. Kayıtlı öğrencilere bildirim gönder
        sendAssignmentNotification(request.getCourseId(), courseData, saved);

        return mapToResponse(saved);
    }

    /**
     * Ödev oluşturulduğunda kayıtlı öğrencilere RabbitMQ ile bildirim gönderir.
     */
    private void sendAssignmentNotification(UUID courseId, Map<String, Object> courseData, Assignment assignment) {
        try {
            // Course-service'ten kayıtlı öğrenci ID'lerini çek
            List<UUID> enrolledStudentIds = courseInternalClient.getEnrolledStudentIds(courseId);

            if (enrolledStudentIds == null || enrolledStudentIds.isEmpty()) {
                log.info("Derste kayıtlı öğrenci yok, ödev bildirimi gönderilmedi.");
                return;
            }

            String courseTitle = courseData.get("title") != null ? courseData.get("title").toString() : "Bilinmeyen Ders";
            String courseCode = courseData.get("code") != null ? courseData.get("code").toString() : "";

            AssignmentNotificationEvent event = new AssignmentNotificationEvent(
                    courseId,
                    courseTitle,
                    courseCode,
                    "ASSIGNMENT",
                    assignment.getTitle(),
                    assignment.getDescription(),
                    enrolledStudentIds
            );

            assignmentProducer.sendAssignmentCreatedNotification(event);
            log.info("Ödev bildirimi gönderildi: {} öğrenciye -> {} ({})",
                    enrolledStudentIds.size(), LogValues.safe(assignment.getTitle()), LogValues.safe(courseTitle));
        } catch (Exception e) {
            log.error("Ödev bildirimi gönderilemedi: {}", e.getMessage());
        }
    }

    public List<AssignmentResponse> getAssignmentsByCourse(UUID courseId) {
        return assignmentRepository.findByCourseId(courseId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = STUDENT_ASSIGNMENTS, allEntries = true)
    public void deleteAssignment(UUID id) {
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("ASSIGNMENT_NOT_FOUND", "Ödev bulunamadı"));
        assessmentRules.requireDeletable(assignment);
        List<String> files = assignmentFiles.of(List.of(assignment));
        assignmentRepository.delete(assignment);
        minioService.deleteFilesAfterCommit(files);
    }

    @CacheEvict(value = STUDENT_ASSIGNMENTS, allEntries = true)
    public AssignmentResponse updateAssignment(Assignment assignment, AssignmentUpdateRequest request, UUID actorId) {
        return mapToResponse(assessmentRules.update(assignment, request, actorId));
    }

    public List<AssignmentChangeResponse> changes(UUID assignmentId) {
        return assessmentRules.changes(assignmentId);
    }

    // BİR DERSE AİT TÜM TESLİMLERİ GETİR (Akademisyen için)
    public List<SubmissionSummaryDTO> getSubmissionsByCourse(UUID courseId) {
        // Önce bu derse ait tüm ödevleri bul
        List<UUID> assignmentIds = assignmentRepository.findByCourseId(courseId).stream()
                .map(Assignment::getId)
                .toList();
        if (assignmentIds.isEmpty()) {
            return List.of();
        }
        return toSubmissionSummaries(submissionRepository.findByAssignmentIdIn(assignmentIds));
    }

    // BİR ÖDEVE AİT TÜM TESLİMLERİ GETİR (Akademisyen için)
    public List<SubmissionSummaryDTO> getSubmissionsByAssignment(UUID assignmentId) {
        return toSubmissionSummaries(submissionRepository.findByAssignmentId(assignmentId));
    }

    // ÖĞRENCİNİN TÜM ÖDEVLERİNİ GETİR (Teslim durumuyla birlikte)
    @Cacheable(value = STUDENT_ASSIGNMENTS, key = "#studentId")
    public List<MyAssignmentDTO> getStudentAssignments(UUID studentId) {
        // Öğrencinin teslimleri
        List<GroupMember> memberships = groupMemberRepository.findByStudentId(studentId);
        Map<UUID, GroupMember> membershipBySet = memberships.stream()
                .collect(Collectors.toMap(GroupMember::getGroupSetId, m -> m, (a, b) -> a));
        Map<UUID, String> groupNames = groupRepository.findAllById(memberships.stream().map(GroupMember::getGroupId).toList())
                .stream().collect(Collectors.toMap(CourseGroup::getId, CourseGroup::getName));
        List<AssignmentSubmission> groupSubmissions = memberships.isEmpty() ? List.of()
                : submissionRepository.findByGroupIdIn(memberships.stream().map(GroupMember::getGroupId).toList());
        Map<UUID, Map<UUID, BigDecimal>> overrides = groupWork.overrides(groupSubmissions.stream().map(AssignmentSubmission::getId).toList());
        List<AssignmentSubmission> submissions = Stream.concat(
                submissionRepository.findByStudentId(studentId).stream().filter(s -> s.getGroupId() == null),
                groupSubmissions.stream()).toList();

        Set<UUID> courseIds = new HashSet<>();
        try {
            courseIds.addAll(courseInternalClient.getActiveCourseIds(studentId));
        } catch (Exception e) {
            log.warn("Could not fetch active courses of student {}: {}", studentId, e.getMessage());
        }
        List<UUID> submittedAssignmentIds = submissions.stream().map(AssignmentSubmission::getAssignmentId).distinct().toList();
        if (!submittedAssignmentIds.isEmpty()) {
            assignmentRepository.findAllById(submittedAssignmentIds).forEach(a -> courseIds.add(a.getCourseId()));
        }
        if (courseIds.isEmpty()) {
            return List.of();
        }
        List<Assignment> allAssignments = assignmentRepository.findByCourseIdIn(courseIds);
        Map<UUID, AssignmentExtension> extensions = extensionRepository.findByStudentId(studentId).stream()
                .collect(Collectors.toMap(AssignmentExtension::getAssignmentId, e -> e, (a, b) -> a));

        return allAssignments.stream().map(assignment -> {
            normalizeAssignmentFileUrlIfNeeded(assignment);

            MyAssignmentDTO dto = new MyAssignmentDTO();
            dto.setId(assignment.getId());
            dto.setTitle(assignment.getTitle());
            dto.setDescription(assignment.getDescription());
            dto.setDueDate(assignment.getDueDate());
            dto.setCourseId(assignment.getCourseId());
            dto.setFileUrl(assignment.getFileUrl());
            dto.setType(assignment.getType());
            dto.setAiPolicy(assignment.getAiPolicy());
            dto.setWeight(assignment.getWeight());
            dto.setMaxPoints(assignment.getMaxPoints());
            dto.setGradesPublished(assignment.gradesPublished());
            GroupMember membership = assignment.isGroupWork() ? membershipBySet.get(assignment.getGroupSetId()) : null;
            DeadlinePolicy.Window window = assignment.isGroupWork()
                    ? (membership != null ? groupWork.groupWindow(assignment, membership.getGroupId()) : DeadlinePolicy.window(assignment, null))
                    : DeadlinePolicy.window(assignment, extensions.get(assignment.getId()));
            dto.setLatePenaltyPercent(assignment.getLatePenaltyPercent());
            dto.setEffectiveDueDate(window.due());
            dto.setEffectiveLateUntil(window.lateUntil());
            dto.setGroupSetId(assignment.getGroupSetId());
            if (membership != null) {
                dto.setGroupId(membership.getGroupId());
                dto.setGroupName(groupNames.get(membership.getGroupId()));
            }

            // Bu ödeve ait teslim var mı?
            submissions.stream()
                    .filter(sub -> sub.getAssignmentId().equals(assignment.getId()))
                    .filter(sub -> !assignment.isGroupWork() || (membership != null && membership.getGroupId().equals(sub.getGroupId())))
                    .findFirst()
                    .ifPresent(submission -> {
                        normalizeSubmissionFileUrlIfNeeded(submission);

                        MySubmissionDTO subDto = new MySubmissionDTO();
                        subDto.setSubmissionId(submission.getId());
                        subDto.setSubmittedAt(submission.getSubmittedAt());
                        if (assignment.gradesPublished()) {
                            BigDecimal grade = overrides.getOrDefault(submission.getId(), Map.of())
                                    .getOrDefault(studentId, submission.getGrade());
                            subDto.setGrade(grade);
                            subDto.setFeedback(submission.getFeedback());
                            subDto.setFinalGrade(DeadlinePolicy.finalGrade(assignment, grade, submission.isLate()));
                        }
                        subDto.setLate(submission.isLate());
                        subDto.setAiUsed(submission.getAiUsed());
                        subDto.setAiNote(submission.getAiNote());
                        subDto.setTextContent(submission.getTextContent());
                        dto.setSubmission(subDto);
                    });

            return dto;
        }).collect(Collectors.toList());
    }

    public AssignmentResponse toResponse(Assignment assignment) {
        return mapToResponse(assignment);
    }

    private AssignmentResponse mapToResponse(Assignment a) {
        normalizeAssignmentFileUrlIfNeeded(a);

        AssignmentResponse res = new AssignmentResponse();
        res.setId(a.getId());
        res.setTitle(a.getTitle());
        res.setDescription(a.getDescription());
        res.setDueDate(a.getDueDate());
        res.setCourseId(a.getCourseId());
        res.setFileUrl(a.getFileUrl());
        res.setType(a.getType());
        res.setAiPolicy(a.getAiPolicy());
        res.setWeight(a.getWeight());
        res.setMaxPoints(a.getMaxPoints());
        res.setGradesPublishedAt(a.getGradesPublishedAt());
        res.setLateUntil(a.getLateUntil());
        res.setGroupSetId(a.getGroupSetId());
        res.setLatePenaltyPercent(a.getLatePenaltyPercent());
        return res;
    }

    private List<SubmissionSummaryDTO> toSubmissionSummaries(List<AssignmentSubmission> submissions) {
        Map<UUID, UserClient.UserProfileDTO> students = studentDirectory.byId(
                submissions.stream().map(AssignmentSubmission::getStudentId).toList());
        Map<UUID, Assignment> assignments = assignmentRepository.findAllById(
                        submissions.stream().map(AssignmentSubmission::getAssignmentId).distinct().toList()).stream()
                .collect(Collectors.toMap(Assignment::getId, a -> a));
        Map<UUID, String> groupNames = groupRepository.findAllById(submissions.stream().map(AssignmentSubmission::getGroupId)
                        .filter(Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(CourseGroup::getId, CourseGroup::getName));
        return submissions.stream()
                .map(submission -> {
                    SubmissionSummaryDTO dto = mapToSubmissionSummary(submission, students.get(submission.getStudentId()),
                            assignments.get(submission.getAssignmentId()));
                    dto.setGroupId(submission.getGroupId());
                    dto.setGroupName(groupNames.get(submission.getGroupId()));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    private SubmissionSummaryDTO mapToSubmissionSummary(AssignmentSubmission submission, UserClient.UserProfileDTO userProfile,
                                                        Assignment assignment) {
        normalizeSubmissionFileUrlIfNeeded(submission);

        SubmissionSummaryDTO dto = new SubmissionSummaryDTO();
        dto.setSubmissionId(submission.getId());
        dto.setStudentId(submission.getStudentId());
        dto.setSubmissionFileUrl(submission.getSubmissionFileUrl());
        dto.setSubmittedAt(submission.getSubmittedAt());
        dto.setGrade(submission.getGrade());
        dto.setLate(submission.isLate());
        dto.setAiUsed(submission.getAiUsed());
        dto.setAiNote(submission.getAiNote());
        dto.setTextContent(submission.getTextContent());
        if (assignment != null) {
            dto.setFinalGrade(DeadlinePolicy.finalGrade(assignment, submission.getGrade(), submission.isLate()));
        }

        if (userProfile != null) {
            dto.setStudentName(userProfile.getFirstName() + " " + userProfile.getLastName());
            dto.setStudentNumber(userProfile.getStudentNumber());
        } else {
            dto.setStudentName("Bilinmeyen Öğrenci");
            dto.setStudentNumber("N/A");
        }

        return dto;
    }

    private void normalizeAssignmentFileUrlIfNeeded(Assignment assignment) {
        String currentUrl = assignment.getFileUrl();
        String normalizedUrl = minioService.normalizeToFullUrl(currentUrl);

        if (normalizedUrl != null && !normalizedUrl.equals(currentUrl)) {
            assignment.setFileUrl(normalizedUrl);
            assignmentRepository.save(assignment);
        }
    }

    private void normalizeSubmissionFileUrlIfNeeded(AssignmentSubmission submission) {
        String currentUrl = submission.getSubmissionFileUrl();
        String normalizedUrl = minioService.normalizeToFullUrl(currentUrl);

        if (normalizedUrl != null && !normalizedUrl.equals(currentUrl)) {
            submission.setSubmissionFileUrl(normalizedUrl);
            submissionRepository.save(submission);
        }
    }

    // DOSYA İNDİRME
    public Resource downloadFile(String fileUrl) {
        InputStream inputStream = minioService.downloadFile(fileUrl);
        return new InputStreamResource(inputStream);
    }

    public String getOriginalFileName(String fileUrl) {
        return minioService.extractOriginalFileName(fileUrl);
    }
}
