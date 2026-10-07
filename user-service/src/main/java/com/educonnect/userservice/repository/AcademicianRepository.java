package com.educonnect.userservice.repository;

import com.educonnect.userservice.models.Academician;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AcademicianRepository extends JpaRepository<Academician, UUID> {

    // Adı VEYA Soyadı, girilen metni (query) içerenleri bul (Case Insensitive)
    @Query("select a from Academician a where a.employmentStatus = 'ACTIVE' "
            + "and replace(upper(concat(a.firstName, ' ', a.lastName)), 'İ', 'I') like concat('%', :query, '%') "
            + "order by a.lastName, a.firstName")
    List<Academician> searchActive(@Param("query") String query, Limit limit);
}
