package com.educonnect.courseservice.service;

import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import com.educonnect.courseservice.dto.MaterialRequest;
import com.educonnect.courseservice.dto.MaterialResponse;
import com.educonnect.courseservice.dto.MaterialUpdateRequest;
import com.educonnect.courseservice.exception.CourseNotFoundException;
import com.educonnect.courseservice.exception.UnauthorizedCourseAccessException;
import com.educonnect.courseservice.model.Course;
import com.educonnect.courseservice.model.CourseMaterial;
import com.educonnect.courseservice.model.CourseStatus;
import com.educonnect.courseservice.model.MaterialKind;
import com.educonnect.courseservice.repository.CourseMaterialRepository;
import com.educonnect.courseservice.repository.CourseRepository;
import com.educonnect.courseservice.repository.EnrollmentRepository;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class CourseMaterialService {

    private static final Comparator<CourseMaterial> ORDER = Comparator
            .comparing(CourseMaterial::getSection, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(CourseMaterial::getSortOrder)
            .thenComparing(CourseMaterial::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));

    private final CourseRepository courseRepository;
    private final CourseMaterialRepository materialRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final CourseStaffAccess staffAccess;
    private final MinioService minioService;
    private final CourseNotifier notifier;

    public CourseMaterialService(CourseRepository courseRepository,
                                 CourseMaterialRepository materialRepository,
                                 EnrollmentRepository enrollmentRepository,
                                 CourseStaffAccess staffAccess,
                                 MinioService minioService,
                                 CourseNotifier notifier) {
        this.courseRepository = courseRepository;
        this.materialRepository = materialRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.staffAccess = staffAccess;
        this.minioService = minioService;
        this.notifier = notifier;
    }

    @Transactional(readOnly = true)
    public List<MaterialResponse> list(UUID courseId, UUID viewerId, boolean viewerIsAdmin) {
        Course course = course(courseId);
        boolean staff = viewerIsAdmin || staffAccess.isStaff(course, viewerId);
        if (!staff && !enrolled(courseId, viewerId)) {
            throw new UnauthorizedCourseAccessException("Bu dersin materyallerini görme yetkiniz yok.");
        }
        return materialRepository.findByCourseId(courseId).stream()
                .filter(material -> staff || material.isVisible())
                .sorted(ORDER)
                .map(this::response)
                .toList();
    }

    public MaterialResponse create(UUID courseId, UUID actorId, MaterialRequest request, MultipartFile file) {
        Course course = editable(courseId, actorId);
        CourseMaterial material = new CourseMaterial(course.getId(), request.kind(), actorId);
        material.setTitle(request.title().strip());
        material.setDescription(blankToNull(request.description()));
        material.setSection(blankToNull(request.section()));
        material.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        material.setVisible(!Boolean.FALSE.equals(request.visible()));
        if (request.kind() == MaterialKind.FILE) {
            if (file == null || file.isEmpty()) {
                throw new BadRequestException("MATERIAL_FILE_REQUIRED", "Dosya materyali için dosya yüklenmeli.");
            }
            material.setFileUrl(minioService.uploadAttachment(file));
        } else {
            material.setLinkUrl(validLink(request.linkUrl()));
        }
        CourseMaterial saved = materialRepository.save(material);
        if (saved.isVisible()) {
            announce(course, saved);
        }
        return response(saved);
    }

    public MaterialResponse update(UUID courseId, UUID materialId, UUID actorId, MaterialUpdateRequest request) {
        Course course = editable(courseId, actorId);
        CourseMaterial material = material(courseId, materialId);
        boolean wasVisible = material.isVisible();
        if (request.title() != null) {
            material.setTitle(request.title().strip());
        }
        if (request.description() != null) {
            material.setDescription(blankToNull(request.description()));
        }
        if (request.section() != null) {
            material.setSection(blankToNull(request.section()));
        }
        if (request.sortOrder() != null) {
            material.setSortOrder(request.sortOrder());
        }
        if (request.visible() != null) {
            material.setVisible(request.visible());
        }
        if (request.linkUrl() != null) {
            if (material.getKind() != MaterialKind.LINK) {
                throw new BadRequestException("MATERIAL_NOT_LINK", "Bağlantı yalnız bağlantı materyalinde değiştirilebilir.");
            }
            material.setLinkUrl(validLink(request.linkUrl()));
        }
        CourseMaterial saved = materialRepository.save(material);
        if (!wasVisible && saved.isVisible()) {
            announce(course, saved);
        }
        return response(saved);
    }

    public void delete(UUID courseId, UUID materialId, UUID actorId) {
        editable(courseId, actorId);
        CourseMaterial material = material(courseId, materialId);
        materialRepository.delete(material);
        if (material.getFileUrl() != null) {
            minioService.deleteFilesAfterCommit(List.of(material.getFileUrl()));
        }
    }

    @Transactional(readOnly = true)
    public CourseMaterial downloadable(UUID courseId, UUID materialId, UUID viewerId, boolean viewerIsAdmin) {
        Course course = course(courseId);
        CourseMaterial material = material(courseId, materialId);
        boolean staff = viewerIsAdmin || staffAccess.isStaff(course, viewerId);
        if (!staff && !(material.isVisible() && enrolled(courseId, viewerId))) {
            throw new UnauthorizedCourseAccessException("Bu materyali indirme yetkiniz yok.");
        }
        if (material.getFileUrl() == null) {
            throw new NotFoundException("FILE_NOT_FOUND", "Bu materyalin dosyası yok.");
        }
        return material;
    }

    public Resource open(CourseMaterial material) {
        return new InputStreamResource(minioService.downloadFile(material.getFileUrl()));
    }

    public String fileName(CourseMaterial material) {
        return minioService.extractOriginalFileName(material.getFileUrl());
    }

    private Course editable(UUID courseId, UUID actorId) {
        Course course = course(courseId);
        staffAccess.requireTeacher(course, actorId, "Materyali yalnızca dersin koordinatörü veya hocası yönetebilir.");
        if (course.getStatus() == CourseStatus.ARCHIVED) {
            throw new ConflictException("COURSE_READ_ONLY", "Arşivlenmiş dersin materyalleri değiştirilemez.");
        }
        return course;
    }

    private Course course(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new CourseNotFoundException("Ders bulunamadı: " + courseId));
    }

    private CourseMaterial material(UUID courseId, UUID materialId) {
        return materialRepository.findById(materialId)
                .filter(material -> material.getCourseId().equals(courseId))
                .orElseThrow(() -> new NotFoundException("MATERIAL_NOT_FOUND", "Materyal bulunamadı."));
    }

    private boolean enrolled(UUID courseId, UUID userId) {
        return userId != null && enrollmentRepository.existsByCourseIdAndStudentIdAndIsActive(courseId, userId, true);
    }

    private MaterialResponse response(CourseMaterial m) {
        return new MaterialResponse(m.getId(), m.getCourseId(), m.getTitle(), m.getDescription(), m.getSection(),
                m.getSortOrder(), m.getKind(), m.getFileUrl() != null ? minioService.extractOriginalFileName(m.getFileUrl()) : null,
                m.getLinkUrl(), m.isVisible(), m.getCreatedBy(), m.getCreatedAt(), m.getUpdatedAt());
    }

    private static String validLink(String link) {
        if (link == null || link.isBlank()) {
            throw new BadRequestException("MATERIAL_LINK_REQUIRED", "Bağlantı materyali için adres girilmeli.");
        }
        try {
            URI uri = new URI(link.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("https") || scheme.equals("http")) || uri.getHost() == null) {
                throw new BadRequestException("INVALID_MATERIAL_LINK", "Bağlantı http veya https ile başlayan geçerli bir adres olmalı.");
            }
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new BadRequestException("INVALID_MATERIAL_LINK", "Bağlantı http veya https ile başlayan geçerli bir adres olmalı.");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void announce(Course course, CourseMaterial material) {
        notifier.notifyStudents(course, "COURSE_MATERIAL", "Yeni ders materyali: " + material.getTitle(),
                course.getTitle() + " dersine yeni materyal eklendi: " + material.getTitle()
                        + (material.getSection() != null ? " (" + material.getSection() + ")" : ""));
    }
}
