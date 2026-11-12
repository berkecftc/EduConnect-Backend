package com.educonnect.clubservice.repository;

import com.educonnect.clubservice.model.ClubFinanceEntry;
import com.educonnect.clubservice.model.FinanceEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClubFinanceEntryRepository extends JpaRepository<ClubFinanceEntry, UUID> {

    Optional<ClubFinanceEntry> findByRequestId(UUID requestId);

    List<ClubFinanceEntry> findByRequestIdIn(Collection<UUID> requestIds);

    List<ClubFinanceEntry> findByClubIdAndAcademicYearOrderByOccurredOnDescCreatedAtDesc(UUID clubId, int academicYear);

    @Query("select coalesce(sum(e.amount), 0) from ClubFinanceEntry e where e.clubId = :clubId "
            + "and e.academicYear = :academicYear and e.type = :type and e.approvedAt is not null")
    BigDecimal approvedTotal(@Param("clubId") UUID clubId, @Param("academicYear") int academicYear,
                             @Param("type") FinanceEntryType type);

    @Query("select coalesce(sum(e.amount), 0) from ClubFinanceEntry e where e.clubId = :clubId "
            + "and e.type = :type and e.approvedAt is not null")
    BigDecimal approvedTotal(@Param("clubId") UUID clubId, @Param("type") FinanceEntryType type);
}
