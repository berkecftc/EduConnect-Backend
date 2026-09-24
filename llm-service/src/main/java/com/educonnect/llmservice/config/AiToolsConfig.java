package com.educonnect.llmservice.config;

import com.educonnect.llmservice.client.AssignmentServiceClient;
import com.educonnect.llmservice.service.UnifiedAgentService;
import org.springframework.ai.chat.model.ToolContext;
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
import java.util.function.BiFunction;
import java.util.function.Function;

@Configuration
public class AiToolsConfig {

    private final AssignmentServiceClient assignmentServiceClient;
    private final VectorStore clubVectorStore;

    public AiToolsConfig(
            AssignmentServiceClient assignmentServiceClient,
            @Qualifier("clubVectorStore") VectorStore clubVectorStore) {
        this.assignmentServiceClient = assignmentServiceClient;
        this.clubVectorStore = clubVectorStore;
    }

    public record GetAssignmentsRequest() {}

    public record PendingAssignment(String courseId, String title, String dueDate, String status) {}

    public record ClubSearchRequest(String query) {}

    public record ClubInfo(String clubName, String description) {}

    @Bean
    @Description("""
            Use this tool to fetch the pending assignments of the current student.

            WHEN to call: the student asks about homework, assignments, deadlines,
            submissions, upcoming tasks, or anything related to their coursework obligations.

            HOW to call: call it without parameters. The student identity is resolved by the system;
            never ask the student for an ID and never try to query another student.

            RESPONSE GUIDANCE:
            - If the returned list is empty: tell the student they have no pending assignments.
            - Otherwise: for each item report the title, courseId, and dueDate clearly in Turkish.
            - Never invent assignment data; only report what this tool returns.
            """)
    public BiFunction<GetAssignmentsRequest, ToolContext, List<PendingAssignment>> getAssignmentsTool() {
        return (request, toolContext) -> {
            Object studentId = toolContext == null ? null : toolContext.getContext().get(UnifiedAgentService.STUDENT_ID_CONTEXT_KEY);
            if (studentId == null) {
                return List.of();
            }
            try {
                return assignmentServiceClient
                        .getMyAssignments(studentId.toString())
                        .stream()
                        .filter(this::isPending)
                        .map(a -> new PendingAssignment(
                                a.courseId(),
                                a.title(),
                                a.dueDate(),
                                "Pending"))
                        .toList();
            } catch (Exception ex) {
                return List.of();
            }
        };
    }

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