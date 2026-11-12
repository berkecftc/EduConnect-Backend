package com.educonnect.courseservice.service;

import com.educonnect.courseservice.exception.UnauthorizedCourseAccessException;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseStaff;
import com.educonnect.courseservice.model.CourseStaffRole;
import com.educonnect.courseservice.repository.CourseStaffRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

@Component
public class CourseStaffAccess {

    private final CourseStaffRepository staffRepository;

    public CourseStaffAccess(CourseStaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    public Optional<CourseStaffRole> roleOf(Course course, UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        if (userId.equals(course.getInstructorId())) {
            return Optional.of(CourseStaffRole.COORDINATOR);
        }
        return staffRepository.findByCourseIdAndUserId(course.getId(), userId).map(CourseStaff::getRole);
    }

    public boolean isStaff(Course course, UUID userId) {
        return roleOf(course, userId).isPresent();
    }

    public void requireCoordinator(Course course, UUID userId) {
        require(course, userId, CourseStaffRole::manages, "Bu işlem yalnızca dersin koordinatörüne aittir.");
    }

    public void requireTeacher(Course course, UUID userId, String message) {
        require(course, userId, CourseStaffRole::teaches, message);
    }

    public void requireStaff(Course course, UUID userId, String message) {
        require(course, userId, role -> true, message);
    }

    private void require(Course course, UUID userId, Predicate<CourseStaffRole> allowed, String message) {
        if (roleOf(course, userId).filter(allowed).isEmpty()) {
            throw new UnauthorizedCourseAccessException(message);
        }
    }
}
