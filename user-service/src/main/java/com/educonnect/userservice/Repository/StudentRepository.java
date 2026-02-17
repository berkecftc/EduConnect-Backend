package com.educonnect.userservice.Repository;

import com.educonnect.userservice.models.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {

    /**
     * Öğrenci numarasına göre öğrenci bulur.
     * @param studentNumber Öğrenci numarası
     * @return Öğrenci varsa Optional içinde, yoksa empty
     */
    Optional<Student> findByStudentNumber(String studentNumber);
}