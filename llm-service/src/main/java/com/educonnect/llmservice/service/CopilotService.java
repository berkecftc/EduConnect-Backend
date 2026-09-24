package com.educonnect.llmservice.service;

import com.educonnect.llmservice.client.CourseServiceClient;
import com.educonnect.llmservice.dto.InstructorCourseSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CopilotService {

    private static final Pattern COURSE_CODE_PATTERN = Pattern.compile("(?i)\\b([A-ZÇĞİÖŞÜ]{2,}\\s*-?\\s*\\d{2,4})\\b");

    static final Duration DRAFT_TTL = Duration.ofMinutes(10);
    static final Set<String> CONFIRM_COMMANDS = Set.of("onayla", "onaylıyorum", "evet", "yayınla", "gönder", "tamam");
    static final Set<String> CANCEL_COMMANDS = Set.of("vazgeç", "vazgec", "iptal", "iptal et", "hayır", "hayir");

    private static final Logger log = LoggerFactory.getLogger(CopilotService.class);

    private final CourseServiceClient courseServiceClient;
    private final LlmRateLimiter rateLimiter;
    private final Clock clock;
    private final Map<String, AnnouncementDraft> drafts = new ConcurrentHashMap<>();

    @Autowired
    public CopilotService(CourseServiceClient courseServiceClient, LlmRateLimiter rateLimiter) {
        this(courseServiceClient, rateLimiter, Clock.systemUTC());
    }

    CopilotService(CourseServiceClient courseServiceClient, LlmRateLimiter rateLimiter, Clock clock) {
        this.courseServiceClient = courseServiceClient;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public String chatWithInstructor(String userMessage, String instructorId) {
        try {
            if (userMessage == null || userMessage.isBlank()) {
                return "Lütfen iletmek istediğiniz mesajı paylaşın.";
            }

            try {
                UUID.fromString(instructorId);
            } catch (IllegalArgumentException ex) {
                return "Kimlik doğrulanamadı. Lütfen tekrar giriş yapıp isteği yeniden deneyin.";
            }

            rateLimiter.acquire(instructorId);

            String command = normalizeText(userMessage);
            AnnouncementDraft draft = activeDraft(instructorId);
            if (CONFIRM_COMMANDS.contains(command)) {
                if (draft == null) {
                    return "Onay bekleyen bir duyuru taslağı yok. Önce duyurmak istediğiniz mesajı ders koduyla birlikte yazın.";
                }
                drafts.remove(instructorId);
                courseServiceClient.createAnnouncement(
                        instructorId,
                        draft.course().id().toString(),
                        new CourseServiceClient.AnnouncementRequest(draft.title(), draft.content())
                );
                return String.format("Duyuru yayınlandı: %s (%s) dersi için '%s' başlıklı duyuru öğrencilere gönderildi.",
                        draft.course().title(), draft.course().code(), draft.title());
            }
            if (CANCEL_COMMANDS.contains(command)) {
                if (draft == null) {
                    return "Onay bekleyen bir duyuru taslağı yok.";
                }
                drafts.remove(instructorId);
                return "Duyuru taslağı iptal edildi; hiçbir şey yayınlanmadı.";
            }

            if (tokenize(command).size() < 3) {
                return "Duyuru hazırlamak için ders kodunu ve duyurmak istediğiniz mesajı yazın. "
                        + "Örnek: \"CS101 dersi yarın iptal, telafisi haftaya yapılacak\".";
            }

            List<InstructorCourseSummary> instructorCourses = fetchInstructorCourses(instructorId);
            if (instructorCourses.isEmpty()) {
                return "Size ait bir ders bulunamadı; duyuru hazırlanamadı.";
            }
            Optional<InstructorCourseSummary> matchedCourse = resolveCourse(instructorCourses, userMessage);
            if (matchedCourse.isEmpty() && instructorCourses.size() == 1) {
                matchedCourse = Optional.of(instructorCourses.getFirst());
            }
            if (matchedCourse.isEmpty()) {
                String options = instructorCourses.stream()
                        .map(c -> c.code() + " (" + c.title() + ")")
                        .collect(Collectors.joining(", "));
                return "Duyurunun hangi ders için olduğunu anlayamadım. Lütfen mesajınıza ders kodunu ekleyin. Dersleriniz: " + options;
            }

            InstructorCourseSummary course = matchedCourse.get();
            AnnouncementDraft newDraft = new AnnouncementDraft(course, buildAnnouncementTitle(userMessage),
                    buildAnnouncementContent(userMessage, course), clock.instant().plus(DRAFT_TTL).toEpochMilli());
            drafts.put(instructorId, newDraft);

            return String.format("Duyuru taslağı hazır (henüz YAYINLANMADI).%n%nDers: %s (%s)%nBaşlık: %s%nİçerik:%n%s%n%n"
                            + "Yayınlamak için \"onayla\", vazgeçmek için \"vazgeç\" yazın. Taslak %d dakika geçerlidir.",
                    course.title(), course.code(), newDraft.title(), newDraft.content(), DRAFT_TTL.toMinutes());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Instructor copilot request failed: {}", e.getMessage());
            return "Duyuru işlemi şu anda tamamlanamadı. Lütfen daha sonra tekrar deneyin.";
        }
    }

    private AnnouncementDraft activeDraft(String instructorId) {
        AnnouncementDraft draft = drafts.get(instructorId);
        if (draft != null && clock.millis() > draft.expiresAtMillis()) {
            drafts.remove(instructorId);
            return null;
        }
        return draft;
    }

    record AnnouncementDraft(InstructorCourseSummary course, String title, String content, long expiresAtMillis) {
    }

    private List<InstructorCourseSummary> fetchInstructorCourses(String instructorId) {
        List<InstructorCourseSummary> courses = courseServiceClient.getMyInstructorCourses(instructorId);
        return courses == null ? List.of() : courses;
    }

    private Optional<InstructorCourseSummary> resolveCourse(List<InstructorCourseSummary> courses, String userMessage) {
        if (courses.isEmpty()) {
            return Optional.empty();
        }

        String codeCandidate = extractCourseCode(userMessage);
        if (codeCandidate != null) {
            String normalizedCodeCandidate = normalizeCode(codeCandidate);
            for (InstructorCourseSummary course : courses) {
                if (normalizedCodeCandidate.equals(normalizeCode(course.code()))) {
                    return Optional.of(course);
                }
            }
        }

        String normalizedMessage = normalizeText(userMessage);
        List<String> messageTokens = tokenize(normalizedMessage);

        for (InstructorCourseSummary course : courses) {
            String normalizedTitle = normalizeText(course.title());
            if (!normalizedTitle.isBlank() && normalizedMessage.contains(normalizedTitle)) {
                return Optional.of(course);
            }

            List<String> titleTokens = tokenize(normalizedTitle);
            if (!titleTokens.isEmpty() && messageTokens.containsAll(titleTokens)) {
                return Optional.of(course);
            }
        }

        return Optional.empty();
    }

    private String buildAnnouncementTitle(String userMessage) {
        String normalized = normalizeText(userMessage);
        if (normalized.contains("iptal")) {
            return "Ders iptali ve telafi duyurusu";
        }
        if (normalized.contains("toplant")) {
            return "Ders / görüşme duyurusu";
        }
        return "Duyuru";
    }

    private String buildAnnouncementContent(String userMessage, InstructorCourseSummary course) {
        String courseName = course.title() == null || course.title().isBlank() ? "ilgili ders" : course.title();
        String normalized = normalizeText(userMessage);

        if (normalized.contains("iptal") && normalized.contains("telafi")) {
            return String.format(
                    "Merhaba arkadaşlar,%n%n%s dersimiz iptal edilmiştir; telafi dersi yapılacaktır.%n%nHocanızın notu: %s%n%nAnlayışınız için teşekkür ederim.",
                    courseName, userMessage.trim()
            );
        }

        if (normalized.contains("iptal")) {
            return String.format(
                    "Merhaba arkadaşlar,%n%n%s dersimiz iptal edilmiştir. Yeni tarih ayrıca duyurulacaktır.%n%nHocanızın notu: %s%n%nAnlayışınız için teşekkür ederim.",
                    courseName, userMessage.trim()
            );
        }

        return String.format(
                "Merhaba arkadaşlar,%n%n%s dersi ile ilgili duyuru:%n%n%s",
                courseName, userMessage.trim()
        );
    }

    private String extractCourseCode(String userMessage) {
        Matcher matcher = COURSE_CODE_PATTERN.matcher(userMessage);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String normalizeCode(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("\\s+", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toUpperCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }

        return value
                .toLowerCase(Locale.forLanguageTag("tr"))
                .replaceAll("[\\p{Punct}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        String[] rawTokens = value.split("\\s+");
        List<String> tokens = new ArrayList<>(rawTokens.length);
        for (String token : rawTokens) {
            if (token.length() > 2) {
                tokens.add(token);
            }
        }
        return tokens;
    }
}