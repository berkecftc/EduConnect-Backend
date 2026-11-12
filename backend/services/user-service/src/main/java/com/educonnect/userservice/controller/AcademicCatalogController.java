package com.educonnect.userservice.controller;

import com.educonnect.userservice.dto.request.AcademicUnitRequest;
import com.educonnect.userservice.dto.response.AcademicCatalogResponse;
import com.educonnect.userservice.dto.response.AcademicTitleResponse;
import com.educonnect.userservice.models.AcademicTitle;
import com.educonnect.userservice.service.AcademicCatalogService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users/academic")
public class AcademicCatalogController {

    private final AcademicCatalogService catalogService;

    public AcademicCatalogController(AcademicCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<AcademicCatalogResponse>> catalog() {
        return ResponseEntity.ok(catalogService.catalog(false));
    }

    @GetMapping("/titles")
    public ResponseEntity<List<AcademicTitleResponse>> titles() {
        return ResponseEntity.ok(Arrays.stream(AcademicTitle.values()).map(AcademicTitleResponse::of).toList());
    }

    @GetMapping("/catalog/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AcademicCatalogResponse>> fullCatalog() {
        return ResponseEntity.ok(catalogService.catalog(true));
    }

    @PostMapping("/faculties")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AcademicCatalogResponse>> createFaculty(@Valid @RequestBody AcademicUnitRequest request) {
        catalogService.saveFaculty(null, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.catalog(true));
    }

    @PutMapping("/faculties/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AcademicCatalogResponse>> updateFaculty(@PathVariable UUID id, @Valid @RequestBody AcademicUnitRequest request) {
        catalogService.saveFaculty(id, request);
        return ResponseEntity.ok(catalogService.catalog(true));
    }

    @PostMapping("/departments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AcademicCatalogResponse>> createDepartment(@Valid @RequestBody AcademicUnitRequest request) {
        catalogService.saveDepartment(null, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.catalog(true));
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AcademicCatalogResponse>> updateDepartment(@PathVariable UUID id, @Valid @RequestBody AcademicUnitRequest request) {
        catalogService.saveDepartment(id, request);
        return ResponseEntity.ok(catalogService.catalog(true));
    }

    @PostMapping("/programs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AcademicCatalogResponse>> createProgram(@Valid @RequestBody AcademicUnitRequest request) {
        catalogService.saveProgram(null, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogService.catalog(true));
    }

    @PutMapping("/programs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AcademicCatalogResponse>> updateProgram(@PathVariable UUID id, @Valid @RequestBody AcademicUnitRequest request) {
        catalogService.saveProgram(id, request);
        return ResponseEntity.ok(catalogService.catalog(true));
    }
}
