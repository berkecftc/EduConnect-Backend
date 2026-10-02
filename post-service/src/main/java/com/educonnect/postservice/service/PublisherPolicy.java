package com.educonnect.postservice.service;

import com.educonnect.common.web.ApiException;
import com.educonnect.postservice.dto.ClubAccess;
import com.educonnect.postservice.dto.CourseAccess;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostCategory;
import com.educonnect.postservice.model.PublisherType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PublisherPolicy {

    static final String PREPARE_ANNOUNCEMENT = "PREPARE_ANNOUNCEMENT";

    private final ScopeAccessService scopeAccess;

    public PublisherPolicy(ScopeAccessService scopeAccess) {
        this.scopeAccess = scopeAccess;
    }

    Publication resolve(Viewer viewer, PostCategory category, PublisherType type, UUID clubId, UUID courseId,
                        String publisherName) {
        PublisherType publisher = type != null ? type : PublisherType.STUDENT;
        if (publisher == PublisherType.STUDENT) {
            if (category.official()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "PUBLISHER_REQUIRED",
                        "Duyuru bir kulüp, ders veya kampüs adına yayımlanır; yayıncıyı seçin.");
            }
            if (clubId != null || courseId != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "SCOPE_NOT_ALLOWED",
                        "Forum gönderisi bir kulüp veya ders adına paylaşılamaz.");
            }
            requireForumWriter(viewer);
            return Publication.forum();
        }
        if (!category.official()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CATEGORY",
                    "Kulüp, ders ve kampüs adına yalnız duyuru yayımlanır.");
        }
        return switch (publisher) {
            case CLUB -> club(viewer, clubId, courseId);
            case COURSE -> course(viewer, courseId, clubId);
            case CAMPUS -> campus(viewer, clubId, courseId, publisherName);
            case STUDENT -> throw new IllegalStateException();
        };
    }

    Publication reauthorize(Viewer viewer, Post post) {
        return resolve(viewer, post.getCategory(), post.getPublisherType(), post.getClubId(), post.getCourseId(),
                post.getPublisherName());
    }

    void requireForumWriter(Viewer viewer) {
        if (!viewer.student()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORUM_POSTING_NOT_ALLOWED",
                    "Forumda soru, not ve genel gönderiyi öğrenciler paylaşır; siz okuyabilir ve yorum yazabilirsiniz.");
        }
    }

    ClubAccess requireClubApprover(Viewer viewer, UUID clubId) {
        ClubAccess access = scopeAccess.clubAccess(clubId, viewer.id());
        if (access == null || !access.actingPresident()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_CLUB_APPROVER",
                    "Kulüp duyurularını kulüp başkanı onaylar.");
        }
        return access;
    }

    private Publication club(Viewer viewer, UUID clubId, UUID courseId) {
        if (clubId == null || courseId != null) {
            throw scopeRequired("Kulüp duyurusu için kulübü seçin.");
        }
        ClubAccess access = scopeAccess.clubAccess(clubId, viewer.id());
        if (access != null && (access.actingPresident() || access.advisor())) {
            return new Publication(PublisherType.CLUB, clubId, null, access.clubName(), false);
        }
        if (access != null && access.has(PREPARE_ANNOUNCEMENT)) {
            return new Publication(PublisherType.CLUB, clubId, null, access.clubName(), true);
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "NOT_CLUB_PUBLISHER",
                "Kulüp adına duyuruyu kulüp yönetimi, iletişim sorumlusu veya danışman hazırlar.");
    }

    private Publication course(Viewer viewer, UUID courseId, UUID clubId) {
        if (courseId == null || clubId != null) {
            throw scopeRequired("Ders duyurusu için dersi seçin.");
        }
        CourseAccess access = scopeAccess.courseAccess(courseId, viewer.id());
        if (access == null || !access.instructor()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_COURSE_PUBLISHER",
                    "Ders duyurusunu dersin koordinatörü veya hocası yayımlar.");
        }
        if ("ARCHIVED".equals(access.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "COURSE_ARCHIVED", "Arşivdeki ders için duyuru yayımlanamaz.");
        }
        return new Publication(PublisherType.COURSE, null, courseId, access.displayName(), false);
    }

    private Publication campus(Viewer viewer, UUID clubId, UUID courseId, String publisherName) {
        if (!viewer.campusPublisher()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_CAMPUS_PUBLISHER",
                    "Üniversite geneli duyuruyu kampüs yayıncısı görevli veya admin yayımlar.");
        }
        if (clubId != null || courseId != null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SCOPE_NOT_ALLOWED",
                    "Kampüs duyurusu bir kulübe veya derse bağlanamaz.");
        }
        if (publisherName == null || publisherName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PUBLISHER_NAME_REQUIRED",
                    "Duyuruyu yayımlayan birimin adını yazın.");
        }
        return new Publication(PublisherType.CAMPUS, null, null, publisherName.strip(), false);
    }

    private static ApiException scopeRequired(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "SCOPE_REQUIRED", message);
    }
}
