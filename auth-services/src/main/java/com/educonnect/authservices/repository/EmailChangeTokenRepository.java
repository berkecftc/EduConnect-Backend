package com.educonnect.authservices.repository;

import com.educonnect.authservices.models.EmailChangeToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailChangeTokenRepository extends JpaRepository<EmailChangeToken, UUID> {

    Optional<EmailChangeToken> findByTokenHash(String tokenHash);

    boolean existsByNewEmail(String newEmail);

    @Modifying
    @Query("delete from EmailChangeToken t where t.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    @Modifying
    @Query("delete from EmailChangeToken t where t.expiresAt < :now")
    int deleteExpired(@Param("now") Instant now);
}
