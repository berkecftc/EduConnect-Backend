package com.educonnect.authservices.Repository;

import com.educonnect.authservices.models.StudentRegistrationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StudentRequestRepository extends JpaRepository<StudentRegistrationRequest, Long> {
    Optional<StudentRegistrationRequest> findByEmail(String email);
}

