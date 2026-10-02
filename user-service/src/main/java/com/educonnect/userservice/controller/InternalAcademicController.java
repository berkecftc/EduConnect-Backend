package com.educonnect.userservice.controller;

import com.educonnect.userservice.dto.response.AcademicPlacement;
import com.educonnect.userservice.service.AcademicCatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users/internal/academic")
public class InternalAcademicController {

    private final AcademicCatalogService catalogService;

    public InternalAcademicController(AcademicCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/programs/{programId}")
    public ResponseEntity<AcademicPlacement> program(@PathVariable UUID programId) {
        return ResponseEntity.ok(catalogService.program(programId));
    }

    @GetMapping("/departments/{departmentId}")
    public ResponseEntity<AcademicPlacement> department(@PathVariable UUID departmentId) {
        return ResponseEntity.ok(catalogService.department(departmentId));
    }
}
