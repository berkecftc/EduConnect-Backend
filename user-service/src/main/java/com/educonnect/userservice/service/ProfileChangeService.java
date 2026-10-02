package com.educonnect.userservice.service;

import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.ForbiddenException;
import com.educonnect.common.web.NotFoundException;
import com.educonnect.userservice.client.AuthStaffClient;
import com.educonnect.userservice.dto.request.ProfileChangeRequestDto;
import com.educonnect.userservice.dto.response.AcademicPlacement;
import com.educonnect.userservice.dto.response.ProfileChangeResponse;
import com.educonnect.userservice.models.AcademicTitle;
import com.educonnect.userservice.models.Academician;
import com.educonnect.userservice.models.ProfileChangeRequest;
import com.educonnect.userservice.models.Student;
import com.educonnect.userservice.repository.AcademicianRepository;
import com.educonnect.userservice.repository.ProfileChangeRequestRepository;
import com.educonnect.userservice.repository.StudentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
@Transactional
public class ProfileChangeService {

    private static final String STUDENT_VERIFIER = "STUDENT_VERIFIER";
    private static final String STAFF_VERIFIER = "STAFF_VERIFIER";

    private final ProfileChangeRequestRepository requestRepository;
    private final StudentRepository studentRepository;
    private final AcademicianRepository academicianRepository;
    private final AcademicCatalogService catalogService;
    private final ProfileService profileService;
    private final AuthStaffClient authStaffClient;

    public ProfileChangeService(ProfileChangeRequestRepository requestRepository,
                                StudentRepository studentRepository,
                                AcademicianRepository academicianRepository,
                                AcademicCatalogService catalogService,
                                ProfileService profileService,
                                AuthStaffClient authStaffClient) {
        this.requestRepository = requestRepository;
        this.studentRepository = studentRepository;
        this.academicianRepository = academicianRepository;
        this.catalogService = catalogService;
        this.profileService = profileService;
        this.authStaffClient = authStaffClient;
    }

    public ProfileChangeResponse submit(UUID userId, ProfileChangeRequestDto dto) {
        Optional<Student> student = studentRepository.findById(userId);
        Optional<Academician> academician = academicianRepository.findById(userId);
        if (student.isEmpty() && academician.isEmpty()) {
            throw new NotFoundException("PROFILE_NOT_FOUND", "Profil bulunamadı.");
        }
        if (requestRepository.existsByUserIdAndStatus(userId, ProfileChangeRequest.Status.PENDING)) {
            throw new ConflictException("CHANGE_REQUEST_PENDING", "Bekleyen bir profil değişikliği talebiniz var.");
        }
        String currentFirst = academician.map(Academician::getFirstName).orElseGet(() -> student.get().getFirstName());
        String currentLast = academician.map(Academician::getLastName).orElseGet(() -> student.get().getLastName());

        ProfileChangeRequest request = new ProfileChangeRequest();
        request.setUserId(userId);
        request.setFirstName(changed(dto.firstName(), currentFirst));
        request.setLastName(changed(dto.lastName(), currentLast));
        if (dto.title() != null && !dto.title().isBlank()) {
            Academician staff = academician.orElseThrow(() ->
                    new BadRequestException("TITLE_NOT_ALLOWED", "Unvan yalnız akademik personel profilinde bulunur."));
            AcademicTitle title = AcademicTitle.parse(dto.title())
                    .orElseThrow(() -> new BadRequestException("INVALID_TITLE", "Unvan katalogdaki unvanlardan biri olmalı."));
            request.setAcademicTitle(title == staff.getAcademicTitle() ? null : title);
        }
        if (dto.programId() != null) {
            Student enrolled = student.orElseThrow(() ->
                    new BadRequestException("PROGRAM_NOT_ALLOWED", "Program yalnız öğrenci kaydında bulunur."));
            requireActive(placement(() -> catalogService.program(dto.programId()), "PROGRAM"), "PROGRAM");
            request.setProgramId(dto.programId().equals(enrolled.getProgramId()) ? null : dto.programId());
        }
        if (dto.departmentId() != null) {
            Academician staff = academician.orElseThrow(() ->
                    new BadRequestException("DEPARTMENT_NOT_ALLOWED", "Bölüm ataması yalnız personel kaydında bulunur."));
            requireActive(placement(() -> catalogService.department(dto.departmentId()), "DEPARTMENT"), "DEPARTMENT");
            request.setDepartmentId(dto.departmentId().equals(staff.getDepartmentId()) ? null : dto.departmentId());
        }
        if (request.getFirstName() == null && request.getLastName() == null && request.getAcademicTitle() == null
                && request.getProgramId() == null && request.getDepartmentId() == null) {
            throw new BadRequestException("NO_CHANGES", "Talepte mevcut bilgilerden farklı bir değer yok.");
        }
        request.setReason(dto.reason().strip());
        try {
            return ProfileChangeResponse.of(requestRepository.saveAndFlush(request), currentFirst + " " + currentLast);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("CHANGE_REQUEST_PENDING", "Bekleyen bir profil değişikliği talebiniz var.");
        }
    }

    @Transactional(readOnly = true)
    public List<ProfileChangeResponse> mine(UUID userId) {
        return requestRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(request -> ProfileChangeResponse.of(request, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProfileChangeResponse> listForVerifier(UUID verifierId, ProfileChangeRequest.Status status) {
        Predicate<ProfileChangeRequest> scope = verifierScope(verifierId);
        return requestRepository.findByStatusOrderByCreatedAtAsc(status).stream()
                .filter(scope)
                .map(request -> ProfileChangeResponse.of(request, currentName(request.getUserId())))
                .toList();
    }

    public ProfileChangeResponse approveAsVerifier(UUID requestId, UUID verifierId) {
        requireInScope(pending(requestId), verifierId);
        return approve(requestId, verifierId);
    }

    public ProfileChangeResponse rejectAsVerifier(UUID requestId, UUID verifierId, String note) {
        requireInScope(pending(requestId), verifierId);
        return reject(requestId, verifierId, note);
    }

    @Transactional(readOnly = true)
    public List<ProfileChangeResponse> list(ProfileChangeRequest.Status status) {
        return requestRepository.findByStatusOrderByCreatedAtAsc(status).stream()
                .map(request -> ProfileChangeResponse.of(request, currentName(request.getUserId())))
                .toList();
    }

    public ProfileChangeResponse approve(UUID requestId, UUID reviewerId) {
        ProfileChangeRequest request = pending(requestId);
        profileService.applyOfficialChange(request);
        request.review(ProfileChangeRequest.Status.APPROVED, reviewerId, null);
        return ProfileChangeResponse.of(requestRepository.save(request), currentName(request.getUserId()));
    }

    public ProfileChangeResponse reject(UUID requestId, UUID reviewerId, String note) {
        ProfileChangeRequest request = pending(requestId);
        request.review(ProfileChangeRequest.Status.REJECTED, reviewerId, note == null || note.isBlank() ? null : note.strip());
        return ProfileChangeResponse.of(requestRepository.save(request), currentName(request.getUserId()));
    }

    private void requireInScope(ProfileChangeRequest request, UUID verifierId) {
        if (!verifierScope(verifierId).test(request)) {
            throw new ForbiddenException("OUT_OF_SCOPE", "Bu talep yetki kapsamınız dışında.");
        }
    }

    private Predicate<ProfileChangeRequest> verifierScope(UUID verifierId) {
        List<AuthStaffClient.StaffGrant> grants;
        try {
            grants = authStaffClient.grants(verifierId);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Yetkiler şu an doğrulanamıyor. Biraz sonra tekrar deneyin.", e);
        }
        boolean staffVerifier = grants.stream().anyMatch(grant -> STAFF_VERIFIER.equals(grant.permission()));
        boolean allStudents = grants.stream().anyMatch(grant -> STUDENT_VERIFIER.equals(grant.permission()) && grant.facultyId() == null);
        Set<UUID> faculties = grants.stream()
                .filter(grant -> STUDENT_VERIFIER.equals(grant.permission()) && grant.facultyId() != null)
                .map(AuthStaffClient.StaffGrant::facultyId)
                .collect(Collectors.toSet());
        return request -> {
            if (request.getProgramId() == null && academicianRepository.existsById(request.getUserId())) {
                return staffVerifier;
            }
            if (allStudents) {
                return true;
            }
            UUID facultyId = studentFaculty(request);
            return facultyId != null && faculties.contains(facultyId);
        };
    }

    private UUID studentFaculty(ProfileChangeRequest request) {
        UUID programId = studentRepository.findById(request.getUserId()).map(Student::getProgramId).orElse(null);
        if (programId == null) {
            programId = request.getProgramId();
        }
        return programId == null ? null : catalogService.program(programId).facultyId();
    }

    private ProfileChangeRequest pending(UUID requestId) {
        ProfileChangeRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("CHANGE_REQUEST_NOT_FOUND", "Profil değişikliği talebi bulunamadı."));
        if (request.getStatus() != ProfileChangeRequest.Status.PENDING) {
            throw new ConflictException("CHANGE_REQUEST_CLOSED", "Bu talep zaten sonuçlandırılmış.");
        }
        return request;
    }

    private String currentName(UUID userId) {
        return academicianRepository.findById(userId).map(a -> a.getFirstName() + " " + a.getLastName())
                .or(() -> studentRepository.findById(userId).map(s -> s.getFirstName() + " " + s.getLastName()))
                .orElse(null);
    }

    private static String changed(String requested, String current) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        String value = requested.strip();
        return Objects.equals(value, current) ? null : value;
    }

    private static AcademicPlacement placement(Supplier<AcademicPlacement> lookup, String kind) {
        try {
            return lookup.get();
        } catch (NotFoundException e) {
            throw new BadRequestException(kind + "_NOT_FOUND", "Akademik birim bulunamadı.");
        }
    }

    private static void requireActive(AcademicPlacement placement, String kind) {
        if (!placement.active()) {
            throw new BadRequestException(kind + "_INACTIVE", "Akademik birim artık kayıt almıyor.");
        }
    }
}
