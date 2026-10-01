package com.educonnect.courseservice.controller;

import com.educonnect.common.storage.SafeFileNames;
import com.educonnect.courseservice.dto.MaterialRequest;
import com.educonnect.courseservice.dto.MaterialResponse;
import com.educonnect.courseservice.dto.MaterialUpdateRequest;
import com.educonnect.courseservice.model.CourseMaterial;
import com.educonnect.courseservice.service.CourseMaterialService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/courses/{courseId}/materials")
public class CourseMaterialController {

    private static final String USER_ID_HEADER = "X-Authenticated-User-Id";
    private static final String ROLES_HEADER = "X-Authenticated-User-Roles";

    private final CourseMaterialService materialService;

    public CourseMaterialController(CourseMaterialService materialService) {
        this.materialService = materialService;
    }

    @GetMapping
    public ResponseEntity<List<MaterialResponse>> list(@PathVariable UUID courseId,
                                                       @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                                       @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        return ResponseEntity.ok(materialService.list(courseId, UUID.fromString(userIdHeader), isAdmin(roles)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaterialResponse> create(@PathVariable UUID courseId,
                                                   @RequestPart("material") @Valid MaterialRequest request,
                                                   @RequestPart(value = "file", required = false) MultipartFile file,
                                                   @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(materialService.create(courseId, UUID.fromString(userIdHeader), request, file));
    }

    @PutMapping("/{materialId}")
    public ResponseEntity<MaterialResponse> update(@PathVariable UUID courseId,
                                                   @PathVariable UUID materialId,
                                                   @RequestBody @Valid MaterialUpdateRequest request,
                                                   @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        return ResponseEntity.ok(materialService.update(courseId, materialId, UUID.fromString(userIdHeader), request));
    }

    @DeleteMapping("/{materialId}")
    public ResponseEntity<Void> delete(@PathVariable UUID courseId,
                                       @PathVariable UUID materialId,
                                       @RequestHeader(USER_ID_HEADER) String userIdHeader) {
        materialService.delete(courseId, materialId, UUID.fromString(userIdHeader));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{materialId}/file")
    public ResponseEntity<Resource> download(@PathVariable UUID courseId,
                                             @PathVariable UUID materialId,
                                             @RequestHeader(USER_ID_HEADER) String userIdHeader,
                                             @RequestHeader(value = ROLES_HEADER, required = false) String roles) {
        CourseMaterial material = materialService.downloadable(courseId, materialId, UUID.fromString(userIdHeader), isAdmin(roles));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, SafeFileNames.attachmentHeader(materialService.fileName(material)))
                .header("X-Content-Type-Options", "nosniff")
                .body(materialService.open(material));
    }

    private static boolean isAdmin(String rolesHeader) {
        return rolesHeader != null && Arrays.stream(rolesHeader.split(","))
                .map(String::trim)
                .anyMatch("ROLE_ADMIN"::equals);
    }
}
