package com.educonnect.authservices.service;

import com.educonnect.authservices.config.RabbitMQConfig;
import com.educonnect.authservices.dto.message.UserRegisteredMessage;
import com.educonnect.authservices.dto.request.RegisterRequest;
import com.educonnect.authservices.dto.response.AuthResponse;
import com.educonnect.authservices.models.AcademicianRegistrationRequest;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.StudentRegistrationRequest;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.AcademicianRequestRepository;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.messaging.outbox.OutboxPublisher;
import com.educonnect.common.web.BadRequestException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RegistrationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RegistrationService.class);

    private final UserRepository userRepository;
    private final AcademicianRequestRepository requestRepository;
    private final StudentRequestRepository studentRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTService jwtService;
    private final OutboxPublisher outboxPublisher;
    private final RefreshTokenService refreshTokenService;
    private final MinioService minioService;
    private final PasswordPolicy passwordPolicy;
    private final EmailVerificationService emailVerificationService;
    private final InstitutionPolicy institutionPolicy;

    public RegistrationService(UserRepository userRepository,
                               AcademicianRequestRepository requestRepository,
                               StudentRequestRepository studentRequestRepository,
                               PasswordEncoder passwordEncoder,
                               JWTService jwtService,
                               OutboxPublisher outboxPublisher,
                               RefreshTokenService refreshTokenService,
                               MinioService minioService,
                               PasswordPolicy passwordPolicy,
                               EmailVerificationService emailVerificationService,
                               InstitutionPolicy institutionPolicy) {
        this.userRepository = userRepository;
        this.requestRepository = requestRepository;
        this.studentRequestRepository = studentRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.outboxPublisher = outboxPublisher;
        this.refreshTokenService = refreshTokenService;
        this.minioService = minioService;
        this.passwordPolicy = passwordPolicy;
        this.emailVerificationService = emailVerificationService;
        this.institutionPolicy = institutionPolicy;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        passwordPolicy.validateNewPassword(request.getPassword(), request.getEmail());
        institutionPolicy.requireStudentEmail(request.getEmail());
        String studentNumber = institutionPolicy.requireStudentNumber(request.getStudentId());
        institutionPolicy.requireStudentNumberAvailable(studentNumber, null);
        institutionPolicy.requireProgram(request.getProgramId());

        Set<Role> roles = Stream.of(Role.ROLE_STUDENT).collect(Collectors.toSet());

        var user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                roles
        );
        user.setStudentNumber(studentNumber);
        user.setEmailVerifiedAt(emailVerificationService.verifiedAtForNewAccount());

        User savedUser = userRepository.save(user);
        emailVerificationService.sendVerification(savedUser.getEmail(), request.getFirstName());

        Set<String> roleStrings = roles.stream().map(Role::name).collect(Collectors.toSet());

        UserRegisteredMessage message = new UserRegisteredMessage(
                savedUser.getId(),
                request.getFirstName(),
                request.getLastName(),
                savedUser.getEmail(),
                roleStrings,
                studentNumber,
                request.getDepartment()
        );
        message.setProgramId(request.getProgramId());
        message.setEntryYear(request.getEntryYear());

        outboxPublisher.publish(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                message
        );

        var jwtToken = jwtService.generateToken(savedUser);
        String refreshToken = refreshTokenService.issue(savedUser.getId());
        return AuthResponses.of(jwtToken, refreshToken, "User registered successfully.", savedUser);
    }

    @Transactional
    public void requestStudentAccount(RegisterRequest request, MultipartFile studentDocument) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BadRequestException("EMAIL_ALREADY_REGISTERED", "Email already registered");
        }
        if (studentRequestRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BadRequestException("STUDENT_REQUEST_ALREADY_EXISTS", "Bu email ile zaten bir başvuru mevcut");
        }
        institutionPolicy.requireStudentEmail(request.getEmail());
        String studentNumber = institutionPolicy.requireStudentNumber(request.getStudentId());
        institutionPolicy.requireStudentNumberAvailable(studentNumber, null);
        institutionPolicy.requireProgram(request.getProgramId());

        if (studentDocument == null || studentDocument.isEmpty()) {
            throw new IllegalArgumentException("Öğrenci belgesi zorunludur");
        }
        passwordPolicy.validateNewPassword(request.getPassword(), request.getEmail());

        UUID tempId = UUID.randomUUID();
        String studentDocumentUrl = minioService.uploadStudentDocument(studentDocument, tempId);
        LOGGER.info("Öğrenci belgesi yüklendi: {}", studentDocumentUrl);

        StudentRegistrationRequest stuReq = new StudentRegistrationRequest();
        stuReq.setFirstName(request.getFirstName());
        stuReq.setLastName(request.getLastName());
        stuReq.setEmail(request.getEmail());
        stuReq.setPassword(passwordEncoder.encode(request.getPassword()));
        stuReq.setStudentNumber(studentNumber);
        stuReq.setDepartment(request.getDepartment());
        stuReq.setProgramId(request.getProgramId());
        stuReq.setEntryYear(request.getEntryYear());
        stuReq.setStudentDocumentUrl(studentDocumentUrl);
        stuReq.setEmailVerifiedAt(emailVerificationService.verifiedAtForNewAccount());

        studentRequestRepository.save(stuReq);
        emailVerificationService.sendVerification(request.getEmail(), request.getFirstName());

        LOGGER.info("Öğrenci başvurusu alındı; admin onayı bekleniyor.");
    }

    @Transactional
    public void requestAcademicianAccount(RegisterRequest request, MultipartFile idCardImage) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BadRequestException("EMAIL_ALREADY_REGISTERED", "Email already registered");
        }
        institutionPolicy.requireStaffEmail(request.getEmail());
        institutionPolicy.requireDepartment(request.getDepartmentId());

        if (idCardImage == null || idCardImage.isEmpty()) {
            throw new IllegalArgumentException("Akademisyen kimlik kartı fotoğrafı zorunludur");
        }
        passwordPolicy.validateNewPassword(request.getPassword(), request.getEmail());

        Set<Role> roles = Stream.of(Role.ROLE_PENDING_ACADEMICIAN).collect(Collectors.toSet());

        var user = new User(
                request.getEmail(),
                passwordEncoder.encode(request.getPassword()),
                roles
        );
        user.setEmailVerifiedAt(emailVerificationService.verifiedAtForNewAccount());

        User savedUser = userRepository.save(user);

        String idCardImageUrl = minioService.uploadIdCardImage(idCardImage, savedUser.getId());
        LOGGER.info("Akademisyen kimlik kartı yüklendi: {}", idCardImageUrl);

        AcademicianRegistrationRequest accReq = new AcademicianRegistrationRequest();
        accReq.setUserId(savedUser.getId());
        accReq.setFirstName(request.getFirstName());
        accReq.setLastName(request.getLastName());
        accReq.setTitle(request.getTitle());
        accReq.setDepartment(request.getDepartment());
        accReq.setDepartmentId(request.getDepartmentId());
        accReq.setOfficeNumber(request.getOfficeNumber());
        accReq.setIdCardImageUrl(idCardImageUrl);

        requestRepository.save(accReq);
        emailVerificationService.sendVerification(savedUser.getEmail(), request.getFirstName());

        LOGGER.info("Akademisyen başvurusu alındı. UserID: {}", savedUser.getId());
    }
}
