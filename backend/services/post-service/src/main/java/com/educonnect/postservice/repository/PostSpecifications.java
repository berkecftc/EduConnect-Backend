package com.educonnect.postservice.repository;

import com.educonnect.postservice.dto.PostFeedFilter;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.model.PublisherType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public final class PostSpecifications {

    private PostSpecifications() {
    }

    public static Specification<Post> publishedFeed(PostFeedFilter filter, boolean allCourses, Collection<UUID> courseIds) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), PostStatus.PUBLISHED));
            if (filter.category() != null) {
                predicates.add(cb.equal(root.get("category"), filter.category()));
            }
            if (filter.official() != null) {
                predicates.add(filter.official()
                        ? cb.equal(root.get("category"), PostCategory.DUYURU)
                        : cb.notEqual(root.get("category"), PostCategory.DUYURU));
            }
            if (filter.publisherType() != null) {
                predicates.add(cb.equal(root.get("publisherType"), filter.publisherType()));
            }
            if (filter.clubId() != null) {
                predicates.add(cb.equal(root.get("clubId"), filter.clubId()));
            }
            if (filter.courseId() != null) {
                predicates.add(cb.equal(root.get("courseId"), filter.courseId()));
            }
            if (!allCourses) {
                Predicate notCourse = cb.notEqual(root.get("publisherType"), PublisherType.COURSE);
                predicates.add(courseIds.isEmpty() ? notCourse : cb.or(notCourse, root.get("courseId").in(courseIds)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
