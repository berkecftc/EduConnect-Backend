package com.educonnect.postservice.service;

import com.educonnect.postservice.exception.PostNotFoundException;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.model.PublisherType;
import com.educonnect.postservice.repository.CommentRepository;
import com.educonnect.postservice.repository.PostBookmarkRepository;
import com.educonnect.postservice.repository.PostLikeRepository;
import com.educonnect.postservice.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostVisibilityTest {

    private final UUID authorId = UUID.randomUUID();
    private final UUID courseId = UUID.randomUUID();
    private final Viewer author = new Viewer(authorId, Set.of("ROLE_STUDENT"));
    private final Viewer otherStudent = new Viewer(UUID.randomUUID(), Set.of("ROLE_STUDENT"));
    private final Viewer staff = new Viewer(UUID.randomUUID(), Set.of("ROLE_STAFF", "PERM_MODERATOR"));

    @Mock
    private PostRepository postRepository;
    @Mock
    private ScopeAccessService scopeAccess;
    @Mock
    private PostLikeRepository postLikeRepository;
    @Mock
    private PostBookmarkRepository postBookmarkRepository;
    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private PostService postService;

    private PostVisibility visibility;

    @BeforeEach
    void setUp() {
        visibility = new PostVisibility(postRepository, scopeAccess);
    }

    @Test
    void pendingPostIsHiddenFromOtherUsers() {
        Post pending = post(PostStatus.PENDING);
        when(postRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> visibility.requireVisible(pending.getId(), otherStudent))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    void authorSeesOwnPendingPost() {
        Post pending = post(PostStatus.PENDING);

        assertThat(visibility.canSee(pending, author)).isTrue();
    }

    @Test
    void courseAnnouncementIsVisibleOnlyToCourseMembersAndStaff() {
        Post announcement = post(PostStatus.PUBLISHED);
        announcement.setCategory(PostCategory.DUYURU);
        announcement.setPublisherType(PublisherType.COURSE);
        announcement.setCourseId(courseId);
        Viewer member = new Viewer(UUID.randomUUID(), Set.of("ROLE_STUDENT"));
        when(scopeAccess.courseMember(courseId, member.id())).thenReturn(true);
        when(scopeAccess.courseMember(courseId, otherStudent.id())).thenReturn(false);

        assertThat(visibility.canSee(announcement, member)).isTrue();
        assertThat(visibility.canSee(announcement, otherStudent)).isFalse();
        assertThat(visibility.canSee(announcement, staff)).isTrue();
        verify(scopeAccess, never()).courseMember(courseId, staff.id());
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
        post.setCategory(PostCategory.SORU);
        post.setStatus(status);
        return post;
    }
}
