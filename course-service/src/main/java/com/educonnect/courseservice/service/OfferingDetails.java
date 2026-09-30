package com.educonnect.courseservice.service;

import com.educonnect.courseservice.dto.OfferingView;
import com.educonnect.courseservice.model.CatalogCourse;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.Term;
import com.educonnect.courseservice.repository.CatalogCourseRepository;
import com.educonnect.courseservice.repository.TermRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class OfferingDetails {

    private final TermRepository termRepository;
    private final CatalogCourseRepository catalogRepository;

    public OfferingDetails(TermRepository termRepository, CatalogCourseRepository catalogRepository) {
        this.termRepository = termRepository;
        this.catalogRepository = catalogRepository;
    }

    public Lookup lookup(Collection<Course> courses) {
        List<UUID> termIds = courses.stream().map(Course::getTermId).distinct().toList();
        List<UUID> catalogIds = courses.stream().map(Course::getCatalogCourseId).distinct().toList();
        return new Lookup(
                termRepository.findAllById(termIds).stream().collect(Collectors.toMap(Term::getId, Function.identity())),
                catalogRepository.findAllById(catalogIds).stream().collect(Collectors.toMap(CatalogCourse::getId, Function.identity())));
    }

    public record Lookup(Map<UUID, Term> terms, Map<UUID, CatalogCourse> catalogs) {

        public void apply(OfferingView view, Course course) {
            Term term = terms.get(course.getTermId());
            CatalogCourse catalog = catalogs.get(course.getCatalogCourseId());
            view.setTermId(course.getTermId());
            view.setTermLabel(term != null ? term.label() : null);
            view.setSection(course.getSection());
            view.setCatalogCourseId(course.getCatalogCourseId());
            view.setEcts(catalog != null ? catalog.getEcts() : null);
        }
    }
}
