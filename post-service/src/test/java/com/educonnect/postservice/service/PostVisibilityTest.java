package com.educonnect.postservice.service;

import com.educonnect.postservice.client.UserClient;
import com.educonnect.postservice.exception.PostNotFoundException;
import com.educonnect.postservice.messaging.PostEventPublisher;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.PostBookmarkRepository;
import com.educonnect.postservice.repository.PostLikeRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostVisibilityTest {

    private final UUID authorId = UUID.randomUUID();
    private final UUID otherUserId = UUID.randomUUID();

    @Mock
    private PostRepository postRepository;
    @Mock
    private PostEventPublisher eventPublisher;
    @Mock
    private UserClient userClient;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private PostBookmarkRepository postBookmarkRepository;
    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private PostService postService;

    @Test
    void pendingPostIsHiddenFromOtherUsers() {
        Post pending = post(PostStatus.PENDING);
        when(postRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> postService.getPostById(pending.getId(), otherUserId))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void authorSeesOwnPendingPost() {
        Post pending = post(PostStatus.PENDING);
        when(postRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        assertThat(postService.getPostById(pending.getId(), authorId).status()).isEqualTo(PostStatus.PENDING);
    }

    @Test
    void myPostsIncludeEveryStatus() {
        Pageable pageable = PageRequest.of(0, 10);
        when(postRepository.findByAuthorId(authorId, pageable))
                .thenReturn(new PageImpl<>(List.of(post(PostStatus.PUBLISHED), post(PostStatus.REJECTED)), pageable, 2));

        assertThat(postService.getMyPosts(authorId, pageable).getContent())
                .extracting("status")
                .containsExactly(PostStatus.PUBLISHED, PostStatus.REJECTED);
    }

    private Post post(PostStatus status) {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setAuthorId(authorId);
        post.setStatus(status);
        return post;
    }
}
