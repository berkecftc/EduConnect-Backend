package com.educonnect.postservice.service;

import com.educonnect.common.web.ApiException;
import com.educonnect.postservice.exception.PostNotFoundException;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.model.PublisherType;
import com.educonnect.postservice.repository.PostRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PostVisibility {

    private final PostRepository postRepository;
    private final ScopeAccessService scopeAccess;

    public PostVisibility(PostRepository postRepository, ScopeAccessService scopeAccess) {
        this.postRepository = postRepository;
        this.scopeAccess = scopeAccess;
    }

    public boolean canSee(Post post, Viewer viewer) {
        if (viewer.id().equals(post.getAuthorId())) {
            return true;
        }
        if (post.getStatus() != PostStatus.PUBLISHED) {
            return false;
        }
        return post.getPublisherType() != PublisherType.COURSE
                || viewer.seesAllScopes()
                || scopeAccess.courseMember(post.getCourseId(), viewer.id());
    }

    public Post requireVisible(UUID postId, Viewer viewer) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException("Post bulunamadı: " + postId));
        if (!canSee(post, viewer)) {
            throw new PostNotFoundException("Post bulunamadı: " + postId);
        }
        return post;
    }

    public Post requirePublished(UUID postId, Viewer viewer, String message) {
        Post post = requireVisible(postId, viewer);
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new IllegalArgumentException(message);
        }
        return post;
    }

    public Post requireOpenForComments(UUID postId, Viewer viewer) {
        Post post = requirePublished(postId, viewer, "Sadece yayınlanmış postlara yorum yapılabilir.");
        if (post.isCommentsDisabled()) {
            throw new ApiException(HttpStatus.CONFLICT, "COMMENTS_DISABLED", "Bu duyuruya yorum kapalı.");
        }
        return post;
    }
}
