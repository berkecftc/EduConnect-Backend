package com.educonnect.assignmentservice.repository;

import com.educonnect.assignmentservice.model.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<AssignmentSubmission, UUID> {

    // Öğrencinin belirli bir ödeve yaptığı teslimi bul (tekrar teslim için)
    Optional<AssignmentSubmission> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

    // Öğrencinin tüm ödev teslimlerini getir
    List<AssignmentSubmission> findByStudentId(UUID studentId);

    // Bir ödeve yapılan tüm teslimleri getir (akademisyen için)
    List<AssignmentSubmission> findByAssignmentId(UUID assignmentId);

    // Öğrenci bu ödevi teslim etmiş mi?
    boolean existsByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);
}

