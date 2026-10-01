package com.educonnect.assignmentservice.service;

import com.educonnect.assignmentservice.client.CourseInternalClient;
import com.educonnect.assignmentservice.client.UserClient;
import com.educonnect.assignmentservice.dto.GradebookResponse;
import com.educonnect.assignmentservice.dto.GradebookResponse.Cell;
import com.educonnect.assignmentservice.dto.GradebookResponse.Column;
import com.educonnect.assignmentservice.dto.GradebookResponse.Row;
import com.educonnect.assignmentservice.dto.GradebookResponse.Status;
import com.educonnect.assignmentservice.dto.MyGradesResponse;
import com.educonnect.assignmentservice.model.Assignment;
import com.educonnect.assignmentservice.model.AssignmentSubmission;
import com.educonnect.assignmentservice.repository.AssignmentRepository;
import com.educonnect.assignmentservice.repository.SubmissionRepository;
import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
public class GradebookService {

    private static final char BYTE_ORDER_MARK = 0xFEFF;

    private static final Comparator<Assignment> ORDER = Comparator.comparing(Assignment::getDueDate,
            Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(Assignment::getTitle);

    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final CourseInternalClient courseInternalClient;
    private final StudentDirectory studentDirectory;

    public GradebookService(AssignmentRepository assignmentRepository,
                            SubmissionRepository submissionRepository,
                            CourseInternalClient courseInternalClient,
                            StudentDirectory studentDirectory) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.courseInternalClient = courseInternalClient;
        this.studentDirectory = studentDirectory;
    }

    public GradebookResponse gradebook(UUID courseId) {
        List<Assignment> assignments = assessments(courseId);
        List<UUID> studentIds = enrolledStudents(courseId);
        Map<UUID, Map<UUID, AssignmentSubmission>> submissions = submissionsByStudent(assignments);
        Map<UUID, UserClient.UserProfileDTO> profiles = studentDirectory.byId(studentIds);
        List<Row> rows = studentIds.stream().map(studentId -> {
            UserClient.UserProfileDTO profile = profiles.get(studentId);
            List<Cell> cells = cells(assignments, submissions.getOrDefault(studentId, Map.of()));
            return new Row(studentId,
                    profile != null ? profile.getFirstName() + " " + profile.getLastName() : "Bilinmeyen Öğrenci",
                    profile != null ? profile.getStudentNumber() : null,
                    cells, weightedTotal(assignments, cells), gradedWeight(assignments, cells),
                    (int) cells.stream().filter(cell -> cell.status() == Status.NOT_SUBMITTED).count());
        }).sorted(Comparator.comparing(Row::studentNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Row::studentName)).toList();
        return new GradebookResponse(courseId, totalWeight(assignments), columns(assignments), rows);
    }

    public MyGradesResponse myGrades(UUID courseId, UUID studentId) {
        List<Assignment> assignments = assessments(courseId);
        Map<UUID, AssignmentSubmission> mine = submissionRepository.findByStudentId(studentId).stream()
                .collect(Collectors.toMap(AssignmentSubmission::getAssignmentId, s -> s, (a, b) -> a));
        List<Cell> cells = cells(assignments, mine).stream()
                .map(cell -> published(assignments, cell.assignmentId()) || cell.grade() == null ? cell
                        : new Cell(cell.assignmentId(), cell.submissionId(), Status.SUBMITTED, null, cell.late(),
                        cell.submittedAt()))
                .toList();
        return new MyGradesResponse(courseId, columns(assignments), cells, weightedTotal(assignments, cells),
                gradedWeight(assignments, cells));
    }

    public String csv(UUID courseId) {
        GradebookResponse book = gradebook(courseId);
        StringBuilder out = new StringBuilder().append(BYTE_ORDER_MARK);
        out.append(line(Stream.concat(
                Stream.of("Öğrenci No", "Ad Soyad"),
                Stream.concat(book.assessments().stream()
                                .map(c -> c.title() + " (%" + plain(c.weight()) + ", " + plain(c.maxPoints()) + " puan)"),
                        Stream.of("Ağırlıklı Toplam"))).toList()));
        for (Row row : book.students()) {
            List<String> values = new ArrayList<>();
            values.add(Objects.toString(row.studentNumber(), ""));
            values.add(row.studentName());
            row.grades().forEach(cell -> values.add(cell.status() == Status.NOT_SUBMITTED ? "-" : plain(cell.grade())));
            values.add(plain(row.weightedTotal()));
            out.append(line(values));
        }
        return out.toString();
    }

    private List<Assignment> assessments(UUID courseId) {
        return assignmentRepository.findByCourseId(courseId).stream().sorted(ORDER).toList();
    }

    private List<UUID> enrolledStudents(UUID courseId) {
        try {
            List<UUID> ids = courseInternalClient.getEnrolledStudentIds(courseId);
            return ids != null ? ids.stream().distinct().toList() : List.of();
        } catch (FeignException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Dersin öğrenci listesi şu an alınamıyor.");
        }
    }

    private Map<UUID, Map<UUID, AssignmentSubmission>> submissionsByStudent(List<Assignment> assignments) {
        if (assignments.isEmpty()) {
            return Map.of();
        }
        return submissionRepository.findByAssignmentIdIn(assignments.stream().map(Assignment::getId).toList()).stream()
                .collect(Collectors.groupingBy(AssignmentSubmission::getStudentId,
                        Collectors.toMap(AssignmentSubmission::getAssignmentId, s -> s, (a, b) -> a)));
    }

    private static List<Column> columns(List<Assignment> assignments) {
        return assignments.stream().map(a -> new Column(a.getId(), a.getTitle(), a.getType(), a.getWeight(),
                a.getMaxPoints(), a.getDueDate(), a.gradesPublished())).toList();
    }

    private static List<Cell> cells(List<Assignment> assignments, Map<UUID, AssignmentSubmission> submissions) {
        return assignments.stream().map(a -> {
            AssignmentSubmission s = submissions.get(a.getId());
            if (s == null) {
                return new Cell(a.getId(), null, Status.NOT_SUBMITTED, null, false, null);
            }
            return new Cell(a.getId(), s.getId(), s.getGrade() != null ? Status.GRADED : Status.SUBMITTED, s.getGrade(),
                    s.isLate(), s.getSubmittedAt());
        }).toList();
    }

    private static BigDecimal weightedTotal(List<Assignment> assignments, List<Cell> cells) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < assignments.size(); i++) {
            Assignment a = assignments.get(i);
            BigDecimal grade = cells.get(i).grade();
            if (grade != null && a.getWeight().signum() > 0) {
                total = total.add(grade.multiply(a.getWeight()).divide(a.getMaxPoints(), 4, RoundingMode.HALF_UP));
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal gradedWeight(List<Assignment> assignments, List<Cell> cells) {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < assignments.size(); i++) {
            if (cells.get(i).grade() != null) {
                total = total.add(assignments.get(i).getWeight());
            }
        }
        return total;
    }

    private static BigDecimal totalWeight(List<Assignment> assignments) {
        return assignments.stream().map(Assignment::getWeight).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean published(List<Assignment> assignments, UUID assignmentId) {
        return assignments.stream().anyMatch(a -> a.getId().equals(assignmentId) && a.gradesPublished());
    }

    private static String line(List<String> values) {
        return values.stream().map(GradebookService::cell).collect(Collectors.joining(",")) + "\r\n";
    }

    private static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 && !"-".equals(text)) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            text = "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
}
