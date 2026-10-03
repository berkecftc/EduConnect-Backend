package com.educonnect.postservice.controller;

import com.educonnect.postservice.dto.CreatePostRequest;
import com.educonnect.postservice.dto.PostFeedFilter;
import com.educonnect.postservice.dto.PostResponse;
import com.educonnect.postservice.dto.RecentPostDto;
import com.educonnect.postservice.dto.ReviewRequest;
import com.educonnect.postservice.dto.UpdatePostRequest;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PublisherType;
import com.educonnect.postservice.service.PostService;
import com.educonnect.postservice.service.Viewer;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    public ResponseEntity<PostResponse> createPost(
            @RequestBody @Valid CreatePostRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        PostResponse response = postService.createPost(request, Viewer.reader(authenticatedUserId, roles));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{postId}")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable UUID postId,
            @RequestBody @Valid UpdatePostRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        return ResponseEntity.ok(postService.updatePost(postId, request, Viewer.reader(authenticatedUserId, roles)));
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> deletePost(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        postService.deletePost(postId, Viewer.reader(authenticatedUserId, roles).id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<PostResponse>> getPublishedPosts(
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles,
            @RequestParam(required = false) PostCategory category,
            @RequestParam(required = false) PublisherType publisherType,
            @RequestParam(required = false) UUID clubId,
            @RequestParam(required = false) UUID courseId,
            @RequestParam(required = false) Boolean official,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        PostFeedFilter filter = new PostFeedFilter(category, publisherType, clubId, courseId, official);
        return ResponseEntity.ok(postService.getPublishedPosts(filter, pageable, Viewer.reader(authenticatedUserId, roles)));
    }

    @GetMapping("/saved")
    public ResponseEntity<Page<PostResponse>> getSavedPosts(
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(postService.getSavedPosts(Viewer.reader(authenticatedUserId, roles), pageable));
    }

    @GetMapping("/me")
    public ResponseEntity<Page<PostResponse>> getMyPosts(
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(postService.getMyPosts(Viewer.reader(authenticatedUserId, roles).id(), pageable));
    }

    @GetMapping("/{postId}")
    public ResponseEntity<PostResponse> getPostById(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        return ResponseEntity.ok(postService.getPostById(postId, Viewer.reader(authenticatedUserId, roles)));
    }

    @GetMapping("/club/{clubId}/awaiting-approval")
    public ResponseEntity<Page<PostResponse>> getAwaitingClubApproval(
            @PathVariable UUID clubId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC)
            Pageable pageable
    ) {
        return ResponseEntity.ok(postService.getAwaitingClubApproval(clubId, Viewer.reader(authenticatedUserId, roles), pageable));
    }

    @PostMapping("/{postId}/approve")
    public ResponseEntity<PostResponse> approveClubAnnouncement(
            @PathVariable UUID postId,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        return ResponseEntity.ok(postService.approveClubAnnouncement(postId, Viewer.reader(authenticatedUserId, roles)));
    }

    @PostMapping("/{postId}/reject")
    public ResponseEntity<PostResponse> rejectClubAnnouncement(
            @PathVariable UUID postId,
            @RequestBody @Valid ReviewRequest request,
            @RequestHeader("X-Authenticated-User-Id") String authenticatedUserId,
            @RequestHeader("X-Authenticated-User-Roles") String roles
    ) {
        return ResponseEntity.ok(postService.rejectClubAnnouncement(postId, request.note(),
                Viewer.reader(authenticatedUserId, roles)));
    }

    @GetMapping("/internal/users/{userId}/recent")
    public ResponseEntity<List<RecentPostDto>> getRecentPostsByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(postService.getRecentPostsByUser(userId));
    }
}
