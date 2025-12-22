package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.client.CourseClient;
import com.educonnect.assignmentservice.dto.AssignmentRequest;
import com.educonnect.assignmentservice.dto.AssignmentResponse;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final MinioService minioService;
    private final CourseClient courseClient;

    public AssignmentService(AssignmentRepository repo, MinioService minio, CourseClient client) {
        this.assignmentRepository = repo;
        this.minioService = minio;
        this.courseClient = client;
    }

    public AssignmentResponse createAssignment(AssignmentRequest request, MultipartFile file) {
        // 1. Önce böyle bir ders var mı diye Course Service'e sor
        try {
            courseClient.getCourseById(request.getCourseId());
        } catch (Exception e) {
            throw new RuntimeException("Ders bulunamadı! Geçersiz Course ID.");
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
        assignment.setFileUrl(fileUrl);

        Assignment saved = assignmentRepository.save(assignment);
        return mapToResponse(saved);
    }

    public List<AssignmentResponse> getAssignmentsByCourse(UUID courseId) {
        return assignmentRepository.findByCourseId(courseId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public void deleteAssignment(UUID id) {
        assignmentRepository.deleteById(id);
    }

    private AssignmentResponse mapToResponse(Assignment a) {
        AssignmentResponse res = new AssignmentResponse();
        res.setId(a.getId());
        res.setTitle(a.getTitle());
        res.setDescription(a.getDescription());
        res.setDueDate(a.getDueDate());
        res.setCourseId(a.getCourseId());
        res.setFileUrl(a.getFileUrl());
        return res;
    }
}