package com.educonnect.postservice.service;

import com.educonnect.postservice.dto.LikeResponse;
import com.educonnect.postservice.exception.PostNotFoundException;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostLike;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.PostLikeRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostLikeServiceTest {

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private ScopeAccessService scopeAccess;

    private PostLikeService postLikeService;

    @BeforeEach
    void setUp() {
        postLikeService = new PostLikeService(postLikeRepository, new PostVisibility(postRepository, scopeAccess));
    }

    @Test
    void likePost_whenPublishedAndNotLiked_shouldCreateLike() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Viewer viewer = new Viewer(userId, Set.of("ROLE_STUDENT"));
        Post post = new Post();
        post.setStatus(PostStatus.PUBLISHED);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId(postId, userId)).thenReturn(false);
        when(postLikeRepository.countByPostId(postId)).thenReturn(1L);

        LikeResponse response = postLikeService.likePost(postId, viewer);

        assertTrue(response.liked());
        assertEquals(1L, response.likeCount());
        verify(postLikeRepository).save(any(PostLike.class));
    }

    @Test
    void unlikePost_whenLikeExists_shouldRemoveLike() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Viewer viewer = new Viewer(userId, Set.of("ROLE_STUDENT"));
        Post post = new Post();
        post.setStatus(PostStatus.PUBLISHED);

        PostLike postLike = new PostLike();
        postLike.setPostId(postId);
        postLike.setUserId(userId);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postLikeRepository.findByPostIdAndUserId(postId, userId)).thenReturn(Optional.of(postLike));
        when(postLikeRepository.countByPostId(postId)).thenReturn(0L);

        LikeResponse response = postLikeService.unlikePost(postId, viewer);

        assertFalse(response.liked());
        assertEquals(0L, response.likeCount());
        verify(postLikeRepository).delete(postLike);
    }

    @Test
    void likePost_whenPostNotFound_shouldThrowNotFound() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Viewer viewer = new Viewer(userId, Set.of("ROLE_STUDENT"));

        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        assertThrows(PostNotFoundException.class, () -> postLikeService.likePost(postId, viewer));
        verify(postLikeRepository, never()).save(any(PostLike.class));
    }

    @Test
    void likePost_whenOwnPostIsNotPublished_shouldThrowIllegalArgumentException() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Viewer viewer = new Viewer(userId, Set.of("ROLE_STUDENT"));
        Post post = new Post();
        post.setStatus(PostStatus.PENDING);
        post.setAuthorId(userId);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> postLikeService.likePost(postId, viewer)
        );

        assertEquals("Sadece yayınlanmış postlar beğenilebilir.", exception.getMessage());
        verify(postLikeRepository, never()).save(any(PostLike.class));
    }

    @Test
    void likePost_whenSomeoneElsesPostIsNotPublished_shouldLookMissing() {
        UUID postId = UUID.randomUUID();
        Viewer viewer = new Viewer(UUID.randomUUID(), Set.of("ROLE_STUDENT"));
        Post post = new Post();
        post.setStatus(PostStatus.PENDING);
        post.setAuthorId(UUID.randomUUID());

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        assertThrows(PostNotFoundException.class, () -> postLikeService.likePost(postId, viewer));
        verify(postLikeRepository, never()).save(any(PostLike.class));
    }
}
