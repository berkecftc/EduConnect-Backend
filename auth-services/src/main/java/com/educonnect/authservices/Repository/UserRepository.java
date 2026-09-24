package com.educonnect.authservices.Repository;

import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {


    Optional<User> findByEmail(String email);

    // Pending kulüp görevlisi başvuruları
    List<User> findAllByRolesContaining(Role role);

    // ...
    @Query("SELECT u.email FROM User u WHERE u.id IN :ids")
    List<String> findEmailsByIds(@Param("ids") List<UUID> ids);

    boolean existsByRolesContaining(Role role);

    @Modifying
    @Query("UPDATE User u SET u.emailVerifiedAt = :now WHERE u.email = :email AND u.emailVerifiedAt IS NULL")
    int markEmailVerified(@Param("email") String email, @Param("now") Instant now);

    @Query(value = "SELECT COUNT(*) > 0 FROM auth_db.users WHERE email = :email AND locked_until > :now", nativeQuery = true)
    boolean isLoginLocked(@Param("email") String email, @Param("now") Instant now);

    @Modifying
    @Query(value = "UPDATE auth_db.users SET failed_login_attempts = failed_login_attempts + 1 WHERE email = :email", nativeQuery = true)
    int incrementFailedLoginAttempts(@Param("email") String email);

    @Modifying
    @Query(value = "UPDATE auth_db.users SET locked_until = :lockedUntil, failed_login_attempts = 0 "
            + "WHERE email = :email AND failed_login_attempts >= :maxAttempts", nativeQuery = true)
    int lockIfAttemptsExceeded(@Param("email") String email,
                               @Param("maxAttempts") int maxAttempts,
                               @Param("lockedUntil") Instant lockedUntil);

    @Modifying
    @Query(value = "UPDATE auth_db.users SET failed_login_attempts = 0, locked_until = NULL "
            + "WHERE id = :userId AND (failed_login_attempts <> 0 OR locked_until IS NOT NULL)", nativeQuery = true)
    int resetFailedLoginAttempts(@Param("userId") UUID userId);
}