package com.educonnect.postservice.controller;

import com.educonnect.common.storage.SafeFileNames;
import com.educonnect.postservice.service.AttachmentService;
import com.educonnect.postservice.service.Viewer;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts/{postId}/attachment")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> attach(
            @PathVariable UUID postId,
            @RequestPart("file") MultipartFile file,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        String name = attachmentService.attach(postId, file, Viewer.reader(authenticatedUserId, roles));
        return ResponseEntity.ok(Map.of("attachmentName", name));
    }

    @DeleteMapping
    public ResponseEntity<Void> detach(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        attachmentService.detach(postId, Viewer.reader(authenticatedUserId, roles));
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Resource> download(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        AttachmentService.Download download = attachmentService.open(postId, Viewer.reader(authenticatedUserId, roles));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, SafeFileNames.attachmentHeader(download.fileName()))
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(download.content()));
    }
}
