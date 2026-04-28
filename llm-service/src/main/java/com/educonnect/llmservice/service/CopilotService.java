package com.educonnect.llmservice.service;

import com.educonnect.llmservice.client.CourseServiceClient;
import com.educonnect.llmservice.dto.InstructorCourseSummary;
import org.springframework.stereotype.Service;

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

    private final CourseServiceClient courseServiceClient;

    public CopilotService(CourseServiceClient courseServiceClient) {
        this.courseServiceClient = courseServiceClient;
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

            List<InstructorCourseSummary> instructorCourses = fetchInstructorCourses(instructorId);
            Optional<InstructorCourseSummary> matchedCourse = resolveCourse(instructorCourses, userMessage);

            if (matchedCourse.isEmpty()) {
                return "Belirttiğiniz dersi eşleştiremedim. Lütfen ders kodunu veya ders adını kontrol edip tekrar deneyin.";
            }

            InstructorCourseSummary course = matchedCourse.get();
            String announcementTitle = buildAnnouncementTitle(userMessage);
            String announcementContent = buildAnnouncementContent(userMessage, course);

            courseServiceClient.createAnnouncement(
                    instructorId,
                    course.id().toString(),
                    new CourseServiceClient.AnnouncementRequest(announcementTitle, announcementContent)
            );

            return String.format(
                    "Duyuru oluşturuldu: %s (%s) dersi için '%s' başlıklı duyuru yayınlandı.",
                    course.title(),
                    course.code(),
                    announcementTitle
            );
        } catch (Exception e) {
            return "Duyuru işlemi tamamlanamadı: " + e.getMessage();
        }
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

        return courses.size() == 1 ? Optional.of(courses.getFirst()) : Optional.empty();
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
                    "Merhaba arkadaşlar,%n%n%s dersimiz, sağlık durumum nedeniyle yarın iptal edilmiştir. Haftaya telafi dersi yapacağız.%n%nAnlayışınız için teşekkür ederim.",
                    courseName
            );
        }

        if (normalized.contains("iptal")) {
            return String.format(
                    "Merhaba arkadaşlar,%n%n%s dersimiz bugün/yarın iptal edilmiştir. Yeni tarih ayrıca duyurulacaktır.%n%nAnlayışınız için teşekkür ederim.",
                    courseName
            );
        }

        return String.format(
                "Merhaba arkadaşlar,%n%n%s ile ilgili önemli bir duyurudur.%n%nDetaylar için lütfen duyuruyu takip edin.",
                courseName
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