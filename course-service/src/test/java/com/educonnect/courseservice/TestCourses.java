package com.educonnect.courseservice;

import com.educonnect.courseservice.model.CatalogCourse;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.repository.CatalogCourseRepository;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class TestCourses {

    public static final UUID DEFAULT_TERM = UUID.fromString("7e000000-0000-4000-8000-000000002027");

    private static final AtomicInteger NEXT_YEAR = new AtomicInteger(2100);

    private TestCourses() {
    }

    public static int unusedYear() {
        return NEXT_YEAR.getAndIncrement();
    }

    public static Course offering(CatalogCourseRepository catalogRepository, String title, UUID instructorId) {
        String code = "T" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        CatalogCourse catalog = catalogRepository.save(new CatalogCourse(code, title, 3, null, instructorId));
        Course course = new Course();
        course.setTitle(title);
        course.setCode(code);
        course.setCredit(3);
        course.setCapacity(10);
        course.setInstructorId(instructorId);
        course.setTermId(DEFAULT_TERM);
        course.setCatalogCourseId(catalog.getId());
        return course;
    }
}
