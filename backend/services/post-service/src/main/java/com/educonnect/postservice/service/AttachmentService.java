package com.educonnect.postservice.service;

import com.educonnect.common.web.ApiException;
import com.educonnect.postservice.exception.PostNotFoundException;
import com.educonnect.postservice.exception.UnauthorizedPostAccessException;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Set;
import java.util.UUID;

@Service
public class AttachmentService {

    private static final Logger log = LoggerFactory.getLogger(AttachmentService.class);

    private static final Set<PostCategory> ATTACHABLE = Set.of(PostCategory.DERS_NOTU, PostCategory.DUYURU);

    private final PostRepository postRepository;
    private final PostVisibility postVisibility;
    private final ObjectProvider<AttachmentStorage> storage;

    public AttachmentService(PostRepository postRepository, PostVisibility postVisibility,
                             ObjectProvider<AttachmentStorage> storage) {
        this.postRepository = postRepository;
        this.postVisibility = postVisibility;
        this.storage = storage;
    }

    public record Download(String fileName, InputStream content) {
    }

    @Transactional
    public String attach(UUID postId, MultipartFile file, Viewer viewer) {
        Post post = ownedPost(postId, viewer);
        if (!ATTACHABLE.contains(post.getCategory())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ATTACHMENT_NOT_ALLOWED",
                    "Dosya yalnız ders notuna ve duyuruya eklenebilir.");
        }
        AttachmentStorage files = requireStorage();
        String previous = post.getAttachmentUrl();
        String url = files.store(file);
        post.attach(url, files.fileName(url));
        postRepository.save(post);
        if (previous != null) {
            files.deleteAfterCommit(previous);
        }
        log.info("Attachment stored. postId={}, authorId={}", postId, viewer.id());
        return post.getAttachmentName();
    }

    @Transactional
    public void detach(UUID postId, Viewer viewer) {
        Post post = ownedPost(postId, viewer);
        if (post.getAttachmentUrl() == null) {
            throw attachmentMissing();
        }
        requireStorage().deleteAfterCommit(post.getAttachmentUrl());
        post.attach(null, null);
        postRepository.save(post);
    }

    @Transactional(readOnly = true)
    public Download open(UUID postId, Viewer viewer) {
        Post post = postVisibility.requireVisible(postId, viewer);
        if (post.getAttachmentUrl() == null) {
            throw attachmentMissing();
        }
        InputStream content = requireStorage().open(post.getAttachmentUrl()).orElseThrow(AttachmentService::attachmentMissing);
        return new Download(post.getAttachmentName(), content);
    }

    public void discard(Post post) {
        if (post.getAttachmentUrl() != null) {
            storage.ifAvailable(files -> files.deleteAfterCommit(post.getAttachmentUrl()));
        }
    }

    private Post ownedPost(UUID postId, Viewer viewer) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new PostNotFoundException("Post bulunamadı: " + postId));
        if (!viewer.id().equals(post.getAuthorId())) {
            throw new UnauthorizedPostAccessException("Bu işlemi sadece post'un yazarı yapabilir. postId: " + postId);
        }
        if (post.getStatus() == PostStatus.HIDDEN || post.getStatus() == PostStatus.REMOVED) {
            throw new ApiException(HttpStatus.CONFLICT, "CONTENT_LOCKED",
                    "Gizlenen veya kaldırılan gönderi düzenlenemez; karara itiraz edebilirsiniz.");
        }
        return post;
    }

    private AttachmentStorage requireStorage() {
        AttachmentStorage files = storage.getIfAvailable();
        if (files == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ATTACHMENTS_UNAVAILABLE", "Dosya ekleme şu anda kullanılamıyor.");
        }
        return files;
    }

    private static ApiException attachmentMissing() {
        return new ApiException(HttpStatus.NOT_FOUND, "ATTACHMENT_NOT_FOUND", "Gönderinin eki yok.");
    }
}
