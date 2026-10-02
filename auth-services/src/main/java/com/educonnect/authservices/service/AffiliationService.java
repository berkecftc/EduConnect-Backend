package com.educonnect.authservices.service;

import com.educonnect.authservices.dto.request.AcademicianAffiliationRequest;
import com.educonnect.authservices.dto.request.StudentAffiliationRequest;
import com.educonnect.authservices.models.AcademicianRegistrationRequest;
import com.educonnect.authservices.models.AccountType;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.StudentRegistrationRequest;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.AcademicianRequestRepository;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AffiliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AffiliationService.class);

    private final UserRepository userRepository;
    private final StudentRequestRepository studentRequestRepository;
    private final AcademicianRequestRepository academicianRequestRepository;
    private final InstitutionPolicy institutionPolicy;
    private final ProfileNames profileNames;
    private final MinioService minioService;

    public AffiliationService(UserRepository userRepository,
                              StudentRequestRepository studentRequestRepository,
                              AcademicianRequestRepository academicianRequestRepository,
                              InstitutionPolicy institutionPolicy,
                              ProfileNames profileNames,
                              MinioService minioService) {
        this.userRepository = userRepository;
        this.studentRequestRepository = studentRequestRepository;
        this.academicianRequestRepository = academicianRequestRepository;
        this.institutionPolicy = institutionPolicy;
        this.profileNames = profileNames;
        this.minioService = minioService;
    }

    @Transactional
    public void requestStudentAffiliation(String email, StudentAffiliationRequest request, MultipartFile studentDocument) {
        User user = user(email);
        if (AccountType.of(user.getRoles()) != AccountType.ACADEMICIAN) {
            throw new ConflictException("AFFILIATION_NOT_ALLOWED", "Öğrenci kaydı yalnız akademik personel hesabına eklenebilir.");
        }
        if (user.getRoles().contains(Role.ROLE_STUDENT)) {
            throw new ConflictException("AFFILIATION_EXISTS", "Hesabınızda zaten öğrenci kaydı var.");
        }
        if (user.getRoles().contains(Role.ROLE_PENDING_STUDENT)
                || studentRequestRepository.findByUserId(user.getId()).isPresent()
                || studentRequestRepository.findByEmail(user.getEmail()).isPresent()) {
            throw new ConflictException("AFFILIATION_PENDING", "Bekleyen bir öğrenci kaydı başvurunuz var.");
        }
        String studentNumber = institutionPolicy.requireStudentNumber(request.studentNumber());
        institutionPolicy.requireStudentNumberAvailable(studentNumber, null);
        institutionPolicy.requireProgram(request.programId());
        if (studentDocument == null || studentDocument.isEmpty()) {
            throw new BadRequestException("STUDENT_DOCUMENT_REQUIRED", "Öğrenci belgesi zorunludur.");
        }
        ProfileNames.Names names = profileNames.of(user.getId());

        StudentRegistrationRequest stuReq = new StudentRegistrationRequest();
        stuReq.setUserId(user.getId());
        stuReq.setFirstName(names.firstName());
        stuReq.setLastName(names.lastName());
        stuReq.setEmail(user.getEmail());
        stuReq.setStudentNumber(studentNumber);
        stuReq.setDepartment(request.department());
        stuReq.setProgramId(request.programId());
        stuReq.setEntryYear(request.entryYear());
        stuReq.setEmailVerifiedAt(user.getEmailVerifiedAt());
        stuReq.setStudentDocumentUrl(minioService.uploadStudentDocument(studentDocument, user.getId()));
        studentRequestRepository.save(stuReq);

        user.getRoles().add(Role.ROLE_PENDING_STUDENT);
        userRepository.save(user);
        LOGGER.info("Öğrenci kaydı başvurusu alındı. UserID: {}", user.getId());
    }

    @Transactional
    public void requestAcademicianAffiliation(String email, AcademicianAffiliationRequest request, MultipartFile idCardImage) {
        User user = user(email);
        if (AccountType.of(user.getRoles()) != AccountType.STUDENT) {
            throw new ConflictException("AFFILIATION_NOT_ALLOWED", "Personel kaydı yalnız öğrenci hesabına eklenebilir.");
        }
        if (user.getRoles().contains(Role.ROLE_PENDING_ACADEMICIAN)
                || academicianRequestRepository.findByUserId(user.getId()).isPresent()) {
            throw new ConflictException("AFFILIATION_PENDING", "Bekleyen bir personel kaydı başvurunuz var.");
        }
        String title = AcademicTitles.require(request.title());
        institutionPolicy.requireDepartment(request.departmentId());
        if (idCardImage == null || idCardImage.isEmpty()) {
            throw new BadRequestException("ID_CARD_REQUIRED", "Personel kimlik kartı fotoğrafı zorunludur.");
        }
        ProfileNames.Names names = profileNames.of(user.getId());

        AcademicianRegistrationRequest accReq = new AcademicianRegistrationRequest();
        accReq.setUserId(user.getId());
        accReq.setFirstName(names.firstName());
        accReq.setLastName(names.lastName());
        accReq.setTitle(title);
        accReq.setDepartment(request.department());
        accReq.setDepartmentId(request.departmentId());
        accReq.setOfficeNumber(request.officeNumber());
        accReq.setIdCardImageUrl(minioService.uploadIdCardImage(idCardImage, user.getId()));
        academicianRequestRepository.save(accReq);

        user.getRoles().add(Role.ROLE_PENDING_ACADEMICIAN);
        userRepository.save(user);
        LOGGER.info("Personel kaydı başvurusu alındı. UserID: {}", user.getId());
    }

    private User user(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Kullanıcı bulunamadı."));
    }
}
