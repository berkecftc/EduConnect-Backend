package com.educonnect.courseservice.service;

import com.educonnect.common.web.NotFoundException;
import com.educonnect.courseservice.exception.CourseNotFoundException;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.repository.CourseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseFileAccessTest {

    private static final String CANONICAL = "http://localhost:9000/courses-bucket/abc_syllabus.pdf";

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private MinioService minioService;

    @InjectMocks
    private CourseService courseService;

    @Test
    void requireCourseFileUrl_returnsCanonicalUrlOfAnExistingCourseFile() {
        when(minioService.canonicalUrl("https://files.example/courses-bucket/abc_syllabus.pdf?X-Amz=1"))
                .thenReturn(CANONICAL);
        when(courseRepository.existsByImageUrl(CANONICAL)).thenReturn(true);

        assertThat(courseService.requireCourseFileUrl("https://files.example/courses-bucket/abc_syllabus.pdf?X-Amz=1"))
                .isEqualTo(CANONICAL);
    }

    @Test
    void requireCourseFileUrl_rejectsObjectsThatBelongToNoCourse() {
        when(minioService.canonicalUrl("courses-bucket/orphan.pdf"))
                .thenReturn("http://localhost:9000/courses-bucket/orphan.pdf");
        when(courseRepository.existsByImageUrl("http://localhost:9000/courses-bucket/orphan.pdf")).thenReturn(false);

        assertThatThrownBy(() -> courseService.requireCourseFileUrl("courses-bucket/orphan.pdf"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Dosya bulunamadı.");
    }

    @Test
    void requireCourseFileUrl_rejectsBlankUrls() {
        when(minioService.canonicalUrl(" ")).thenReturn(null);

        assertThatThrownBy(() -> courseService.requireCourseFileUrl(" "))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getCourseFileUrl_resolvesTheFileOfTheCourse() {
        UUID courseId = UUID.randomUUID();
        Course course = new Course();
        course.setImageUrl(CANONICAL);
        when(courseRepository.findById(courseId)).thenReturn(Optional.of(course));

        assertThat(courseService.getCourseFileUrl(courseId)).isEqualTo(CANONICAL);
    }

    @Test
    void getCourseFileUrl_failsWhenCourseHasNoFileOrDoesNotExist() {
        UUID withoutFile = UUID.randomUUID();
        UUID missing = UUID.randomUUID();
        when(courseRepository.findById(withoutFile)).thenReturn(Optional.of(new Course()));
        when(courseRepository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courseService.getCourseFileUrl(withoutFile)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> courseService.getCourseFileUrl(missing)).isInstanceOf(CourseNotFoundException.class);
    }
}
