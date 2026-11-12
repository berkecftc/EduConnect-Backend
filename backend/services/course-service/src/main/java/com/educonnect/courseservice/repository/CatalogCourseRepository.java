package com.educonnect.courseservice.repository;

import com.educonnect.courseservice.model.CatalogCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogCourseRepository extends JpaRepository<CatalogCourse, UUID> {

    Optional<CatalogCourse> findByCode(String code);

    @Query("select c from CatalogCourse c where upper(c.code) like concat('%', :code, '%') "
            + "or upper(c.title) like concat('%', upper(:query), '%') order by c.code")
    List<CatalogCourse> search(@Param("code") String code, @Param("query") String query);
}
