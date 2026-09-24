package com.educonnect.authservices.Repository;

import com.educonnect.authservices.models.StudentRegistrationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface StudentRequestRepository extends JpaRepository<StudentRegistrationRequest, Long> {
    Optional<StudentRegistrationRequest> findByEmail(String email);

    @Modifying
    @Query("UPDATE StudentRegistrationRequest r SET r.emailVerifiedAt = :now WHERE r.email = :email AND r.emailVerifiedAt IS NULL")
    int markEmailVerified(@Param("email") String email, @Param("now") Instant now);
}
