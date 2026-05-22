package com.educonnect.llmservice.config;

import com.educonnect.llmservice.client.AssignmentServiceClient;
import com.educonnect.llmservice.client.CourseServiceClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * Defines the tool (function) library available to Spring AI agents.
 *
 * Each @Bean here becomes a callable tool. The @Description text is the
 * prompt the LLM reads to decide WHEN and HOW to call the tool.
 * Good descriptions are the most critical part of prompt engineering for agents.
 *
 * Student tools  : getAssignmentsTool, searchClubsTool
 * Instructor tool: createAnnouncementTool
 */
@Configuration
public class AiToolsConfig {

    private final AssignmentServiceClient assignmentServiceClient;
    private final CourseServiceClient courseServiceClient;
    private final VectorStore clubVectorStore;

    public AiToolsConfig(
            AssignmentServiceClient assignmentServiceClient,
            CourseServiceClient courseServiceClient,
            @Qualifier("clubVectorStore") VectorStore clubVectorStore) {
        this.assignmentServiceClient = assignmentServiceClient;
        this.courseServiceClient = courseServiceClient;
        this.clubVectorStore = clubVectorStore;
    }

    // ── Request / Response records ──────────────────────────────────────────

    public record GetAssignmentsRequest(String studentId) {}

    /**
     * Anti-Corruption Layer: Internal domain language (English) is translated
     * here so the LLM always works with Turkish-friendly field names, preventing
     * confusion between source service DTOs and agent-visible data.
     */
    public record PendingAssignment(String courseId, String title, String dueDate, String status) {}

    public record ClubSearchRequest(String query) {}

    public record ClubInfo(String clubName, String description) {}

    public record CreateAnnouncementRequest(
            String instructorId, String courseId, String title, String content) {}

    public record AnnouncementResult(boolean success, String message) {}

    // ── Student Tool 1: Pending Assignments ─────────────────────────────────

    @Bean
    @Description("""
            Use this tool to fetch the pending assignments of the current student.

            WHEN to call: the student asks about homework, assignments, deadlines,
            submissions, upcoming tasks, or anything related to their coursework obligations.

            HOW to call: always pass the studentId that was given to you in the system prompt
            as the 'studentId' parameter — never ask the student for their ID.

            RESPONSE GUIDANCE:
            - If the returned list is empty: tell the student they have no pending assignments.
            - Otherwise: for each item report the title, courseId, and dueDate clearly in Turkish.
            - Never invent assignment data; only report what this tool returns.
            """)
    public Function<GetAssignmentsRequest, List<PendingAssignment>> getAssignmentsTool() {
        return request -> {
            try {
                return assignmentServiceClient
                        .getMyAssignments(request.studentId())
                        .stream()
                        .filter(this::isPending)
                        .map(a -> new PendingAssignment(
                                a.courseId(),
                                a.title(),
                                a.dueDate(),
                                "Pending"))
                        .toList();
            } catch (Exception ex) {
                // Return empty list — the LLM will respond gracefully per the description above.
                return List.of();
            }
        };
    }

    // ── Student Tool 2: Club Search ──────────────────────────────────────────

    @Bean
    @Description("""
            Use this tool to search for university clubs and communities that match the student's interests.

            WHEN to call: the student asks about clubs, communities, societies, activities,
            joining a group, or mentions a hobby or academic interest they want to pursue.

            HOW to call: extract the core topic or interest keyword from the student's message
            and pass it as the 'query' parameter (e.g. "yazılım", "müzik", "yapay zeka", "spor").

            RESPONSE GUIDANCE:
            - Present up to 3 relevant clubs with their name and a brief description.
            - If no clubs match, suggest the student check the platform's club directory.
            - Never fabricate club names or descriptions.
            """)
    public Function<ClubSearchRequest, List<ClubInfo>> searchClubsTool() {
        return request -> {
            try {
                return clubVectorStore
                        .similaritySearch(SearchRequest.builder()
                                .query(request.query())
                                .topK(5)
                                .similarityThreshold(0.50)
                                .build())
                        .stream()
                        .map(this::toClubInfo)
                        .toList();
            } catch (Exception ex) {
                return List.of();
            }
        };
    }

    // ── Instructor Tool: Create Announcement ────────────────────────────────

    @Bean
    @Description("""
            Use this tool to publish a new announcement to a specific course on behalf of an instructor.

            WHEN to call: the instructor asks to announce something to their students
            (e.g. cancelled class, exam date, important notice).

            HOW to call:
            - instructorId is provided in the system prompt — always use it as-is.
            - courseId must be explicitly stated by the instructor; if missing, ask for it first.
            - Generate a professional, polite Turkish title and content based on the instructor's request.
            """)
    public Function<CreateAnnouncementRequest, AnnouncementResult> createAnnouncementTool() {
        return request -> {
            try {
                courseServiceClient.createAnnouncement(
                        request.instructorId(),
                        request.courseId(),
                        new CourseServiceClient.AnnouncementRequest(request.title(), request.content()));
                return new AnnouncementResult(true, "Announcement published successfully.");
            } catch (Exception ex) {
                return new AnnouncementResult(false, "Failed to publish announcement: " + ex.getMessage());
            }
        };
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private boolean isPending(AssignmentServiceClient.AssignmentResponse assignment) {
        boolean notSubmitted = assignment.submission() == null
                || assignment.submission().submissionId() == null;
        boolean notOverdue = !isOverdue(assignment.dueDate());
        return notSubmitted && notOverdue;
    }

    private boolean isOverdue(String dueDate) {
        if (dueDate == null || dueDate.isBlank()) {
            return false;
        }
        try {
            return LocalDateTime.parse(dueDate).isBefore(LocalDateTime.now());
        } catch (Exception ex) {
            return false;
        }
    }

    private ClubInfo toClubInfo(Document document) {
        String content = document.getText();
        String name = parseField(content, "Kulüp Adı:");
        String description = parseField(content, "Açıklama:");
        return new ClubInfo(
                name.isBlank() ? "İsimsiz Kulüp" : name,
                description.isBlank() ? "Açıklama mevcut değil." : description);
    }

    private String parseField(String content, String fieldLabel) {
        return Arrays.stream(content.split("\\n"))
                .filter(line -> line.trim().startsWith(fieldLabel))
                .map(line -> line.substring(line.indexOf(':') + 1).trim())
                .findFirst()
                .orElse("");
    }
}