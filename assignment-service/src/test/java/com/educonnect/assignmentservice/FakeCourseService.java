package com.educonnect.assignmentservice;

import com.educonnect.common.security.ServiceTokenProvider;
import com.educonnect.common.test.TestTokens;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@TestConfiguration(proxyBeanMethods = false)
public class FakeCourseService {

    private static final Pattern COURSE = Pattern.compile("^/api/courses/([0-9a-f-]{36})$");
    private static final Pattern ENROLLED_IDS = Pattern.compile("^/api/courses/internal/([0-9a-f-]{36})/enrolled-students/ids$");
    private static final Pattern ACTIVE_COURSES = Pattern.compile("^/api/courses/internal/students/([0-9a-f-]{36})/course-ids$");
    private static final Pattern ACCESS = Pattern.compile("^/api/courses/internal/([0-9a-f-]{36})/access/([0-9a-f-]{36})$");

    private static final Map<UUID, UUID> INSTRUCTORS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<UUID>> ENROLLMENTS = new ConcurrentHashMap<>();
    private static final Map<UUID, String> STATUSES = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<UUID, String>> STAFF = new ConcurrentHashMap<>();
    private static final HttpServer SERVER = start();

    public static void course(UUID courseId, UUID instructorId, UUID... enrolledStudents) {
        INSTRUCTORS.put(courseId, instructorId);
        ENROLLMENTS.put(courseId, Set.of(enrolledStudents));
        STATUSES.put(courseId, "ACTIVE");
        STAFF.put(courseId, new ConcurrentHashMap<>(Map.of(instructorId, "COORDINATOR")));
    }

    public static void staff(UUID courseId, UUID userId, String role) {
        STAFF.get(courseId).put(userId, role);
    }

    public static void status(UUID courseId, String status) {
        STATUSES.put(courseId, status);
    }

    @Bean
    public DynamicPropertyRegistrar fakeCourseServiceUrl() {
        String url = "http://localhost:" + SERVER.getAddress().getPort();
        return registry -> {
            registry.add("spring.cloud.openfeign.client.config.course-service.url", () -> url);
            registry.add("spring.cloud.openfeign.client.config.courseInternalClient.url", () -> url);
        };
    }

    @Bean
    public ServiceTokenProvider serviceTokenProvider() {
        return new ServiceTokenProvider(null, "assignment-service", "unused", "unused", Duration.ZERO, Clock.systemUTC()) {
            @Override
            public String getToken() {
                return TestTokens.service("assignment-service");
            }
        };
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext("/", FakeCourseService::handle);
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        Matcher course = COURSE.matcher(path);
        Matcher enrolled = ENROLLED_IDS.matcher(path);
        Matcher active = ACTIVE_COURSES.matcher(path);
        Matcher access = ACCESS.matcher(path);
        if (path.startsWith("/api/courses/internal/") && (authorization == null || !authorization.startsWith("Bearer "))) {
            respond(exchange, 401, "{}");
        } else if (access.matches() && INSTRUCTORS.containsKey(UUID.fromString(access.group(1)))) {
            UUID courseId = UUID.fromString(access.group(1));
            UUID userId = UUID.fromString(access.group(2));
            String role = STAFF.get(courseId).get(userId);
            respond(exchange, 200, "{\"courseId\":\"" + courseId + "\",\"status\":\"" + STATUSES.get(courseId)
                    + "\",\"instructor\":" + ("COORDINATOR".equals(role) || "INSTRUCTOR".equals(role))
                    + ",\"enrolled\":" + ENROLLMENTS.get(courseId).contains(userId)
                    + ",\"staffRole\":" + (role == null ? "null" : "\"" + role + "\"") + "}");
        } else if (enrolled.matches() && ENROLLMENTS.containsKey(UUID.fromString(enrolled.group(1)))) {
            respond(exchange, 200, jsonArray(ENROLLMENTS.get(UUID.fromString(enrolled.group(1)))));
        } else if (active.matches()) {
            UUID student = UUID.fromString(active.group(1));
            respond(exchange, 200, jsonArray(ENROLLMENTS.entrySet().stream()
                    .filter(entry -> entry.getValue().contains(student))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet())));
        } else if (course.matches() && INSTRUCTORS.containsKey(UUID.fromString(course.group(1)))) {
            UUID courseId = UUID.fromString(course.group(1));
            respond(exchange, 200, "{\"id\":\"" + courseId + "\",\"title\":\"Yetki Testi\",\"code\":\"AUTH-101\",\"instructorId\":\""
                    + INSTRUCTORS.get(courseId) + "\"}");
        } else {
            respond(exchange, 404, "{}");
        }
    }

    private static String jsonArray(Set<UUID> ids) {
        return ids.stream().map(id -> "\"" + id + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
