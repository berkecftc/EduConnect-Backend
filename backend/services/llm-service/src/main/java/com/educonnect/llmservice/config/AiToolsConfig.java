package com.educonnect.llmservice.config;

import com.educonnect.llmservice.service.PendingAssignments;
import com.educonnect.llmservice.service.UnifiedAgentService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.educonnect.llmservice.service.ClubCatalogIndex;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

@Configuration
public class AiToolsConfig {

    private final PendingAssignments pendingAssignments;
    private final ClubCatalogIndex clubCatalog;

    public AiToolsConfig(PendingAssignments pendingAssignments, ClubCatalogIndex clubCatalog) {
        this.pendingAssignments = pendingAssignments;
        this.clubCatalog = clubCatalog;
    }

    public record GetAssignmentsRequest() {}

    public record ClubSearchRequest(String query) {}


    public static final String ASSIGNMENTS_TOOL = "getAssignmentsTool";

    public static final String CLUBS_TOOL = "searchClubsTool";

    private static final String ASSIGNMENTS_DESCRIPTION = """
            Returns the current student's assignments that are not submitted yet and can still be submitted,
            with course code and title, type, due date, status (including late submission windows),
            the instructor's AI policy (aiPolicy) and how you may help with it (aiHelp).

            WHEN to call: the student asks about homework, assignments, deadlines or submissions,
            or asks for help with something that may be graded work.

            HOW to call: call it without parameters. The student identity is resolved by the system;
            never ask the student for an ID and never try to query another student.

            RESPONSE GUIDANCE:
            - If the returned list is empty: tell the student they have no pending assignments.
            - Otherwise report title, course code and title, due date and status in Turkish.
            - Follow aiHelp strictly when helping with that assignment.
            - Never invent assignment data; only report what this tool returns.
            """;

    private static final String CLUBS_DESCRIPTION = """
            Use this tool to search for university clubs and communities that match the student's interests.

            WHEN to call: the student asks about clubs, communities, societies, activities,
            joining a group, or mentions a hobby or academic interest they want to pursue.

            HOW to call: extract the core topic or interest keyword from the student's message
            and pass it as the 'query' parameter (e.g. "yazılım", "müzik", "yapay zeka", "spor").

            RESPONSE GUIDANCE:
            - Present up to 3 relevant clubs with their name, category and a brief description.
            - Only active clubs that accept members are in the catalog.
            - If no clubs match, suggest the student check the platform's club directory.
            - Never fabricate club names or descriptions.
            """;

    @Bean
    public ToolCallback getAssignmentsTool() {
        return FunctionToolCallback.builder(ASSIGNMENTS_TOOL, assignmentsFunction())
                .description(ASSIGNMENTS_DESCRIPTION)
                .inputType(GetAssignmentsRequest.class)
                .build();
    }

    @Bean
    public ToolCallback searchClubsTool() {
        return FunctionToolCallback.builder(CLUBS_TOOL, clubsFunction())
                .description(CLUBS_DESCRIPTION)
                .inputType(ClubSearchRequest.class)
                .build();
    }

    BiFunction<GetAssignmentsRequest, ToolContext, List<PendingAssignments.PendingAssignment>> assignmentsFunction() {
        return (request, toolContext) -> {
            Object studentId = toolContext == null ? null : toolContext.getContext().get(UnifiedAgentService.STUDENT_ID_CONTEXT_KEY);
            if (studentId == null) {
                return List.of();
            }
            try {
                return pendingAssignments.of(studentId.toString());
            } catch (Exception ex) {
                return List.of();
            }
        };
    }

    Function<ClubSearchRequest, List<ClubCatalogIndex.ClubInfo>> clubsFunction() {
        return request -> {
            try {
                return clubCatalog.search(request.query());
            } catch (Exception ex) {
                return List.of();
            }
        };
    }

}
