package com.educonnect.courseservice.service;

import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import com.educonnect.courseservice.client.UserClient;
import com.educonnect.courseservice.client.UserLookup;
import com.educonnect.courseservice.dto.CoordinatorTransferRequest;
import com.educonnect.courseservice.dto.CourseStaffRequest;
import com.educonnect.courseservice.dto.CourseStaffResponse;
import com.educonnect.courseservice.dto.UserSummaryDto;
import com.educonnect.courseservice.exception.CourseNotFoundException;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseStaff;
import com.educonnect.courseservice.model.CourseStaffRole;
import com.educonnect.courseservice.model.CourseStatus;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.CourseStaffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Transactional
public class CourseStaffService {

    private static final Logger log = LoggerFactory.getLogger(CourseStaffService.class);

    private final CourseRepository courseRepository;
    private final CourseStaffRepository staffRepository;
    private final CourseStaffAccess staffAccess;
    private final CourseCaches courseCaches;
    private final UserClient userClient;
    private final StaffEligibility staffEligibility;

    public CourseStaffService(CourseRepository courseRepository,
                              CourseStaffRepository staffRepository,
                              CourseStaffAccess staffAccess,
                              CourseCaches courseCaches,
                              UserClient userClient,
                              StaffEligibility staffEligibility) {
        this.courseRepository = courseRepository;
        this.staffRepository = staffRepository;
        this.staffAccess = staffAccess;
        this.courseCaches = courseCaches;
        this.userClient = userClient;
        this.staffEligibility = staffEligibility;
    }

    @Transactional(readOnly = true)
    public List<CourseStaffResponse> list(UUID courseId, UUID viewerId, boolean viewerIsAdmin) {
        Course course = find(courseId);
        if (course.getStatus() == CourseStatus.DRAFT && !viewerIsAdmin && !staffAccess.isStaff(course, viewerId)) {
            throw new CourseNotFoundException("Ders bulunamadı: " + courseId);
        }
        return responses(course);
    }

    public List<CourseStaffResponse> add(UUID courseId, UUID actorId, CourseStaffRequest request) {
        Course course = find(courseId);
        staffAccess.requireCoordinator(course, actorId);
        requireChangeable(course);
        requireAssignable(request.role());
        if (staffAccess.isStaff(course, request.userId())) {
            throw new ConflictException("STAFF_EXISTS", "Bu kullanıcı zaten dersin kadrosunda.");
        }
        staffEligibility.requireAcademician(request.userId());
        staffRepository.save(new CourseStaff(courseId, request.userId(), request.role(), actorId));
        courseCaches.evictInstructorCourses(request.userId());
        log.info("Course staff added: course={}, user={}, role={}", courseId, request.userId(), request.role());
        return responses(course);
    }

    public List<CourseStaffResponse> changeRole(UUID courseId, UUID actorId, UUID userId, CourseStaffRole role) {
        Course course = find(courseId);
        staffAccess.requireCoordinator(course, actorId);
        requireChangeable(course);
        requireAssignable(role);
        CourseStaff member = member(course, userId);
        member.changeRole(role);
        staffRepository.save(member);
        courseCaches.evictInstructorCourses(userId);
        return responses(course);
    }

    public void remove(UUID courseId, UUID actorId, UUID userId, boolean actorIsAdmin) {
        Course course = find(courseId);
        if (!actorIsAdmin && !userId.equals(actorId)) {
            staffAccess.requireCoordinator(course, actorId);
        }
        requireChangeable(course);
        CourseStaff member = member(course, userId);
        staffRepository.delete(member);
        courseCaches.evictInstructorCourses(userId);
        log.info("Course staff removed: course={}, user={}, by={}", courseId, userId, actorId);
    }

    public List<CourseStaffResponse> transferCoordinator(UUID courseId, UUID adminId, CoordinatorTransferRequest request) {
        Course course = courseRepository.findByIdForUpdate(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + courseId));
        requireChangeable(course);
        UUID previous = course.getInstructorId();
        UUID next = request.userId();
        if (next.equals(previous)) {
            throw new ConflictException("ALREADY_COORDINATOR", "Bu kullanıcı zaten dersin koordinatörü.");
        }
        if (request.previousCoordinatorRole() != null) {
            requireAssignable(request.previousCoordinatorRole());
        }
        staffEligibility.requireCoordinator(next);
        staffRepository.findByCourseIdAndUserId(courseId, next).ifPresent(staffRepository::delete);
        staffRepository.flush();
        course.setInstructorId(next);
        courseRepository.save(course);
        if (request.previousCoordinatorRole() != null) {
            staffRepository.save(new CourseStaff(courseId, previous, request.previousCoordinatorRole(), adminId));
        }
        courseCaches.evictInstructorCourses(previous);
        courseCaches.evictStaffCourses(course);
        log.info("Course coordinator transferred: course={}, from={}, to={}, by={}", courseId, previous, next, adminId);
        return responses(course);
    }

    private List<CourseStaffResponse> responses(Course course) {
        List<CourseStaff> staff = new ArrayList<>(staffRepository.findByCourseIdOrderByCreatedAtAsc(course.getId()));
        staff.sort(Comparator.comparing(CourseStaff::getRole));
        Map<UUID, UserSummaryDto> users = UserLookup.usersById(userClient, Stream.concat(
                Stream.of(course.getInstructorId()), staff.stream().map(CourseStaff::getUserId)).toList());
        List<CourseStaffResponse> result = new ArrayList<>();
        result.add(response(course.getInstructorId(), CourseStaffRole.COORDINATOR, users, null));
        staff.forEach(member -> result.add(response(member.getUserId(), member.getRole(), users, member)));
        return result;
    }

    private static CourseStaffResponse response(UUID userId, CourseStaffRole role, Map<UUID, UserSummaryDto> users,
                                                CourseStaff member) {
        UserSummaryDto user = users.get(userId);
        return new CourseStaffResponse(userId, role,
                user != null ? user.getFirstName() + " " + user.getLastName() : "Bilinmiyor",
                user != null ? user.getDepartment() : null,
                member != null ? member.getCreatedAt() : null);
    }

    private Course find(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + courseId));
    }

    private CourseStaff member(Course course, UUID userId) {
        if (userId.equals(course.getInstructorId())) {
            throw new ConflictException("COORDINATOR_NOT_EDITABLE",
                    "Koordinatör kadrodan çıkarılamaz veya rolü değiştirilemez; koordinatör devri yönetici tarafından yapılır.");
        }
        return staffRepository.findByCourseIdAndUserId(course.getId(), userId)
                .orElseThrow(() -> new NotFoundException("STAFF_NOT_FOUND", "Kullanıcı bu dersin kadrosunda değil."));
    }

    private static void requireChangeable(Course course) {
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new ConflictException("COURSE_READ_ONLY", "Arşivlenmiş dersin kadrosu değiştirilemez.");
        }
    }

    private static void requireAssignable(CourseStaffRole role) {
        if (!role.assignable()) {
            throw new BadRequestException("COORDINATOR_NOT_ASSIGNABLE",
                    "Koordinatör kadroya eklenemez; koordinatör devri yönetici tarafından yapılır.");
        }
    }
}
