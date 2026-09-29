package com.educonnect.llmservice.service;

import com.educonnect.llmservice.client.CourseServiceClient;
import com.educonnect.llmservice.config.LlmSafetyProperties;
import com.educonnect.llmservice.dto.InstructorCourseSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CopilotServiceTest {

    private final String instructorId = UUID.randomUUID().toString();
    private final InstructorCourseSummary cs101 = new InstructorCourseSummary(UUID.randomUUID(), "Programlamaya Giriş", "CS101");
    private final InstructorCourseSummary ma201 = new InstructorCourseSummary(UUID.randomUUID(), "Lineer Cebir", "MA201");

    @Mock
    private CourseServiceClient courseServiceClient;

    private MutableClock clock;
    private CopilotService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-24T10:00:00Z"));
        LlmRateLimiter rateLimiter = new LlmRateLimiter(new LlmSafetyProperties(null, null, null));
        service = new CopilotService(courseServiceClient, rateLimiter, clock);
    }

    @Test
    void draft_shouldBePreviewedAndNotPublished() {
        when(courseServiceClient.getMyInstructorCourses(instructorId)).thenReturn(List.of(cs101, ma201));

        String reply = service.chatWithInstructor("CS101 dersi yarın iptal, telafisi haftaya", instructorId);

        assertThat(reply).contains("YAYINLANMADI").contains("CS101").contains("onayla");
        verify(courseServiceClient, never()).createAnnouncement(anyString(), anyString(), any());
    }

    @Test
    void confirm_shouldPublishPendingDraftOnce() {
        when(courseServiceClient.getMyInstructorCourses(instructorId)).thenReturn(List.of(cs101, ma201));
        service.chatWithInstructor("CS101 dersi yarın iptal, telafisi haftaya", instructorId);

        String reply = service.chatWithInstructor("Onayla", instructorId);

        ArgumentCaptor<CourseServiceClient.AnnouncementRequest> request =
                ArgumentCaptor.forClass(CourseServiceClient.AnnouncementRequest.class);
        verify(courseServiceClient).createAnnouncement(eq(instructorId), eq(cs101.id().toString()), request.capture());
        assertThat(request.getValue().content()).doesNotContain("sağlık");
        assertThat(reply).startsWith("Duyuru yayınlandı");
        assertThat(service.chatWithInstructor("onayla", instructorId)).contains("taslağı yok");
    }

    @Test
    void cancel_shouldDropDraftWithoutPublishing() {
        when(courseServiceClient.getMyInstructorCourses(instructorId)).thenReturn(List.of(cs101));
        service.chatWithInstructor("yarınki dersimiz iptal edildi arkadaşlar", instructorId);

        assertThat(service.chatWithInstructor("vazgeç", instructorId)).contains("iptal edildi");
        service.chatWithInstructor("onayla", instructorId);

        verify(courseServiceClient, never()).createAnnouncement(anyString(), anyString(), any());
    }

    @Test
    void unmatchedCourseWithSeveralCourses_shouldAskForCourse() {
        when(courseServiceClient.getMyInstructorCourses(instructorId)).thenReturn(List.of(cs101, ma201));

        String reply = service.chatWithInstructor("yarınki ders iptal edildi arkadaşlar", instructorId);

        assertThat(reply).contains("CS101").contains("MA201").doesNotContain("YAYINLANMADI");
    }

    @Test
    void greeting_shouldNotCreateDraft() {
        String reply = service.chatWithInstructor("merhaba", instructorId);

        assertThat(reply).contains("ders kodunu");
        verify(courseServiceClient, never()).getMyInstructorCourses(anyString());
    }

    @Test
    void expiredDraft_shouldNotBePublished() {
        when(courseServiceClient.getMyInstructorCourses(instructorId)).thenReturn(List.of(cs101));
        service.chatWithInstructor("CS101 vize tarihi gelecek hafta pazartesi", instructorId);
        clock.advance(CopilotService.DRAFT_TTL.plusSeconds(1).toMillis());

        assertThat(service.chatWithInstructor("onayla", instructorId)).contains("taslağı yok");
        verify(courseServiceClient, never()).createAnnouncement(anyString(), anyString(), any());
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(long millis) {
            now = now.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
