package com.educonnect.courseservice.controller;

import com.educonnect.common.web.ForbiddenException;
import com.educonnect.courseservice.dto.CatalogCourseResponse;
import com.educonnect.courseservice.dto.TermRequest;
import com.educonnect.courseservice.dto.TermResponse;
import com.educonnect.courseservice.service.CatalogCourseService;
import com.educonnect.courseservice.service.TermService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses")
public class TermController {

    private static final String ROLES_HEADER = "X-Authenticated-User-Roles";

    private final TermService termService;
    private final CatalogCourseService catalogCourseService;

    public TermController(TermService termService, CatalogCourseService catalogCourseService) {
        this.termService = termService;
        this.catalogCourseService = catalogCourseService;
    }

    @GetMapping("/terms")
    public ResponseEntity<List<TermResponse>> terms() {
        return ResponseEntity.ok(termService.terms());
    }

    @GetMapping("/terms/current")
    public ResponseEntity<TermResponse> currentTerm() {
        return ResponseEntity.ok(termService.currentTerm());
    }

    @PostMapping("/terms")
    public ResponseEntity<TermResponse> createTerm(@Valid @RequestBody TermRequest request,
                                                   @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        requireAdmin(roles);
        return ResponseEntity.status(HttpStatus.CREATED).body(termService.create(request));
    }

    @PutMapping("/terms/{termId}")
    public ResponseEntity<TermResponse> updateTerm(@PathVariable UUID termId,
                                                   @Valid @RequestBody TermRequest request,
                                                   @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        requireAdmin(roles);
        return ResponseEntity.ok(termService.update(termId, request));
    }

    @DeleteMapping("/terms/{termId}")
    public ResponseEntity<Void> deleteTerm(@PathVariable UUID termId,
                                           @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        requireAdmin(roles);
        termService.delete(termId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<CatalogCourseResponse>> catalog(@RequestParam(required = false) String q) {
        return ResponseEntity.ok(catalogCourseService.search(q));
    }

    private static void requireAdmin(String roles) {
        boolean admin = roles != null && Arrays.stream(roles.split(",")).map(String::trim).anyMatch("ROLE_ADMIN"::equals);
        if (!admin) {
            throw new ForbiddenException("FORBIDDEN", "Dönemleri yalnızca yönetici düzenleyebilir.");
        }
    }
}
