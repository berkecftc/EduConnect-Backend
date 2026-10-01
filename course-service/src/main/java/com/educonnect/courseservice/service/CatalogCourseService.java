package com.educonnect.courseservice.service;

import com.educonnect.common.web.NotFoundException;
import com.educonnect.courseservice.dto.CatalogCourseResponse;
import com.educonnect.courseservice.model.CatalogCourse;
import com.educonnect.courseservice.repository.CatalogCourseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class CatalogCourseService {

    private final CatalogCourseRepository catalogRepository;

    public CatalogCourseService(CatalogCourseRepository catalogRepository) {
        this.catalogRepository = catalogRepository;
    }

    public CatalogCourse findOrCreate(String code, String title, int credit, Integer ects, UUID createdBy) {
        String normalized = normalize(code);
        return catalogRepository.findByCode(normalized)
                .orElseGet(() -> catalogRepository.save(new CatalogCourse(normalized, title.strip(), credit, ects, createdBy)));
    }

    @Transactional(readOnly = true)
    public List<CatalogCourseResponse> search(String query) {
        String term = query == null ? "" : query.strip();
        return catalogRepository.search(term).stream().limit(50).map(CatalogCourseResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public CatalogCourse find(UUID catalogCourseId) {
        return catalogRepository.findById(catalogCourseId)
                .orElseThrow(() -> new NotFoundException("CATALOG_COURSE_NOT_FOUND", "Katalog dersi bulunamadı."));
    }

    public static String normalize(String code) {
        return code.strip().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
