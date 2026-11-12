package com.educonnect.llmservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class AssignmentPolicyGuard {

    private static final Logger log = LoggerFactory.getLogger(AssignmentPolicyGuard.class);
    private static final Locale TURKISH = Locale.forLanguageTag("tr");
    private static final Pattern STATUS_QUESTION = Pattern.compile(
            "ne zaman|son tarih|teslim tarihi|kaç gün|kaç saat|bitiyor|kapanıyor|durum|geç teslim|kesinti|puan");

    public record Decision(String reply, String systemNote) {

        static Decision none() {
            return new Decision(null, null);
        }
    }

    private final PendingAssignments pendingAssignments;

    public AssignmentPolicyGuard(PendingAssignments pendingAssignments) {
        this.pendingAssignments = pendingAssignments;
    }

    public Decision evaluate(String studentId, String message) {
        List<PendingAssignments.PendingAssignment> mentioned;
        try {
            String text = normalize(message);
            mentioned = pendingAssignments.all(studentId).stream()
                    .filter(assignment -> mentions(text, normalize(assignment.title())))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("Assignment policy could not be checked: {}", e.getMessage());
            return Decision.none();
        }
        if (mentioned.size() != 1) {
            return Decision.none();
        }
        PendingAssignments.PendingAssignment assignment = mentioned.getFirst();
        if ("NONE".equals(assignment.aiPolicy()) && !STATUS_QUESTION.matcher(normalize(message)).find()) {
            return new Decision("\"" + assignment.title() + "\" ödevinde hocanız yapay zekâ kullanımına izin vermiyor; bu yüzden "
                    + "ödevin içeriğine yardım edemem. Son teslim: " + assignment.dueDate() + " (" + assignment.status() + ")."
                    + " Ders materyallerine göz atabilir ya da sorunu hocanıza görüşme saatlerinde sorabilirsiniz.", null);
        }
        return new Decision(null, "The student is asking about the assignment \"" + assignment.title() + "\""
                + (assignment.courseCode() != null ? " (" + assignment.courseCode() + ")" : "")
                + ". Instructor's AI policy for it: " + assignment.aiHelp() + " Follow this strictly.");
    }

    static boolean mentions(String message, String title) {
        if (title.isBlank()) {
            return false;
        }
        if (message.contains(title)) {
            return true;
        }
        List<String> words = Arrays.stream(title.split(" ")).filter(word -> word.length() >= 4).toList();
        long hits = words.stream().filter(message::contains).count();
        return hits >= 2 && hits * 2 >= words.size();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(TURKISH).replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").strip();
    }
}
