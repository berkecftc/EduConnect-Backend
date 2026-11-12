package com.educonnect.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class Problems {

    public static final String MESSAGE = "message";
    public static final String ERROR_CODE = "errorCode";
    public static final String ERRORS = "errors";
    public static final String TIMESTAMP = "timestamp";

    private static final Map<Integer, String> DEFAULT_MESSAGES = Map.ofEntries(
            Map.entry(400, "Geçersiz istek."),
            Map.entry(401, "Oturum açmanız gerekiyor."),
            Map.entry(403, "Bu işlem için yetkiniz yok."),
            Map.entry(404, "Kayıt bulunamadı."),
            Map.entry(405, "Bu işlem bu adreste desteklenmiyor."),
            Map.entry(406, "İstenen yanıt biçimi desteklenmiyor."),
            Map.entry(409, "İşlem mevcut durumla çakışıyor."),
            Map.entry(410, "Bu uç artık kullanılmıyor."),
            Map.entry(413, "Dosya boyutu izin verilen sınırı aşıyor."),
            Map.entry(415, "İstek biçimi desteklenmiyor."),
            Map.entry(422, "İstek işlenemedi."),
            Map.entry(429, "Çok fazla istek gönderildi. Lütfen biraz bekleyin."),
            Map.entry(500, "Beklenmeyen bir hata oluştu."),
            Map.entry(502, "İlgili servisten geçersiz yanıt alındı."),
            Map.entry(503, "Servis şu an kullanılamıyor. Lütfen daha sonra tekrar deneyin."),
            Map.entry(504, "İlgili servis zamanında yanıt vermedi."));

    private static final URI BLANK_TYPE = URI.create("about:blank");

    private Problems() {
    }

    public static ProblemDetail create(HttpStatusCode status, String errorCode, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setDetail(detail);
        return enrich(problem, errorCode, path);
    }

    public static ProblemDetail enrich(ProblemDetail problem, String errorCode, String path) {
        int status = problem.getStatus();
        if (problem.getType() == null) {
            problem.setType(BLANK_TYPE);
        }
        if (!StringUtils.hasText(problem.getDetail())) {
            problem.setDetail(defaultMessage(status));
        }
        if (problem.getInstance() == null && StringUtils.hasText(path)) {
            problem.setInstance(URI.create(path));
        }
        Map<String, Object> properties = problem.getProperties();
        if (properties == null || !properties.containsKey(ERROR_CODE)) {
            problem.setProperty(ERROR_CODE, StringUtils.hasText(errorCode) ? errorCode : defaultErrorCode(status));
        }
        problem.setProperty(MESSAGE, problem.getDetail());
        problem.setProperty(TIMESTAMP, Instant.now().toString());
        return problem;
    }

    public static ProblemDetail withErrors(ProblemDetail problem, List<FieldProblem> errors) {
        problem.setProperty(ERRORS, errors);
        return problem;
    }

    public static ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    public static String defaultMessage(int status) {
        String message = DEFAULT_MESSAGES.get(status);
        if (message != null) {
            return message;
        }
        return status >= 500 ? DEFAULT_MESSAGES.get(500) : DEFAULT_MESSAGES.get(400);
    }

    public static String defaultErrorCode(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        return resolved != null ? resolved.name() : "HTTP_" + status;
    }

    public record FieldProblem(String field, String message) {
    }
}
