package com.educonnect.authservices.service;

import com.educonnect.authservices.models.Role;
import com.educonnect.authservices.models.StaffGrant;
import com.educonnect.authservices.models.StaffPermission;
import com.educonnect.authservices.models.StudentRegistrationRequest;
import com.educonnect.authservices.models.User;
import com.educonnect.authservices.repository.UserRepository;
import com.educonnect.common.web.ForbiddenException;
import com.educonnect.common.web.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
public class VerificationScope {

    private final UserRepository userRepository;
    private final InstitutionPolicy institutionPolicy;

    public VerificationScope(UserRepository userRepository, InstitutionPolicy institutionPolicy) {
        this.userRepository = userRepository;
        this.institutionPolicy = institutionPolicy;
    }

    public Predicate<StudentRegistrationRequest> studentRequests(String verifierEmail) {
        Scope scope = studentScope(verifierEmail);
        if (scope.all()) {
            return request -> true;
        }
        Map<UUID, UUID> facultyOfProgram = new HashMap<>();
        return request -> request.getProgramId() != null && scope.faculties().contains(
                facultyOfProgram.computeIfAbsent(request.getProgramId(), institutionPolicy::facultyOfProgram));
    }

    public void requireStudentRequest(String verifierEmail, StudentRegistrationRequest request) {
        if (!studentRequests(verifierEmail).test(request)) {
            throw new ForbiddenException("OUT_OF_SCOPE", "Bu başvuru yetki kapsamınız dışında.");
        }
    }

    private Scope studentScope(String verifierEmail) {
        User verifier = userRepository.findByEmail(verifierEmail)
                .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Kullanıcı bulunamadı."));
        if (verifier.getRoles().contains(Role.ROLE_ADMIN)) {
            return new Scope(true, Set.of());
        }
        Set<StaffGrant> grants = verifier.getStaffGrants().stream()
                .filter(grant -> grant.getPermission() == StaffPermission.STUDENT_VERIFIER)
                .collect(Collectors.toSet());
        boolean all = grants.stream().anyMatch(grant -> grant.getFacultyId() == null);
        return new Scope(all, grants.stream().map(StaffGrant::getFacultyId).filter(id -> id != null).collect(Collectors.toSet()));
    }

    private record Scope(boolean all, Set<UUID> faculties) {
    }
}
