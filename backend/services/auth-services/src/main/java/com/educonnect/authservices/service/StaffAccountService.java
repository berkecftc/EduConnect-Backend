package com.educonnect.authservices.service;

import com.educonnect.authservices.dto.request.StaffAccountRequest;
import com.educonnect.authservices.dto.response.StaffAccountView;
import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.StaffGrant;
import com.educonnect.authservices.models.StaffPermission;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.StudentRequestRepository;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.web.BadRequestException;
import com.educonnect.common.web.ConflictException;
import com.educonnect.common.web.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class StaffAccountService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StaffAccountService.class);

    private final UserRepository userRepository;
    private final StudentRequestRepository studentRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordService passwordService;
    private final InstitutionPolicy institutionPolicy;
    private final RefreshTokenService refreshTokenService;

    public StaffAccountService(UserRepository userRepository,
                               StudentRequestRepository studentRequestRepository,
                               PasswordEncoder passwordEncoder,
                               PasswordService passwordService,
                               InstitutionPolicy institutionPolicy,
                               RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.studentRequestRepository = studentRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordService = passwordService;
        this.institutionPolicy = institutionPolicy;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public StaffAccountView create(StaffAccountRequest request) {
        String email = request.email().strip();
        if (userRepository.findByEmail(email).isPresent() || studentRequestRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("EMAIL_TAKEN", "Bu e-posta başka bir hesapta veya başvuruda kullanılıyor. Görevli hesabı ayrı bir adresle açılır.");
        }
        institutionPolicy.requireStaffEmail(email);
        Set<StaffGrant> grants = grants(request.grants());

        User user = new User(email, passwordEncoder.encode(OpaqueTokens.generate()), new HashSet<>(Set.of(Role.ROLE_STAFF)));
        user.setDisplayName(request.displayName().strip());
        user.setEmailVerifiedAt(Instant.now());
        user.getStaffGrants().addAll(grants);
        User saved = userRepository.save(user);
        passwordService.sendAccountSetupLink(saved);
        LOGGER.info("Staff account created. UserID: {}, permissions: {}", saved.getId(), saved.permissionAuthorities());
        return StaffAccountView.of(saved);
    }

    @Transactional(readOnly = true)
    public List<StaffAccountView> list() {
        return userRepository.findAllByRolesContaining(Role.ROLE_STAFF).stream()
                .sorted(Comparator.comparing(User::getEmail))
                .map(StaffAccountView::of)
                .toList();
    }

    @Transactional
    public StaffAccountView replaceGrants(UUID userId, List<StaffAccountRequest.Grant> requested) {
        User user = staff(userId);
        Set<StaffGrant> grants = grants(requested);
        user.getStaffGrants().clear();
        user.getStaffGrants().addAll(grants);
        userRepository.save(user);
        refreshTokenService.revokeAllSessions(userId);
        LOGGER.info("Staff permissions replaced. UserID: {}, permissions: {}", userId, user.permissionAuthorities());
        return StaffAccountView.of(user);
    }

    @Transactional(readOnly = true)
    public List<StaffAccountView.Grant> grantsOf(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> StaffAccountView.of(user).grants())
                .orElse(List.of());
    }

    private User staff(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Kullanıcı bulunamadı."));
        if (!user.getRoles().contains(Role.ROLE_STAFF)) {
            throw new ConflictException("NOT_A_STAFF_ACCOUNT", "Yetkiler yalnız görevli hesaplarına verilir.");
        }
        return user;
    }

    private Set<StaffGrant> grants(List<StaffAccountRequest.Grant> requested) {
        Set<StaffGrant> grants = new HashSet<>();
        for (StaffAccountRequest.Grant grant : requested) {
            StaffPermission permission = permission(grant.permission());
            List<UUID> faculties = grant.facultyIds() == null ? List.of() : grant.facultyIds();
            if (faculties.isEmpty()) {
                grants.add(new StaffGrant(permission, null));
                continue;
            }
            if (!permission.facultyScoped()) {
                throw new BadRequestException("SCOPE_NOT_ALLOWED", permission + " yetkisi fakülteyle sınırlanamaz.");
            }
            for (UUID facultyId : faculties) {
                institutionPolicy.requireFaculty(facultyId);
                grants.add(new StaffGrant(permission, facultyId));
            }
        }
        return grants;
    }

    private static StaffPermission permission(String value) {
        try {
            return StaffPermission.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("INVALID_PERMISSION", "Geçersiz yetki: " + value);
        }
    }
}
