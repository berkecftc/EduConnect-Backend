package com.educonnect.common.web;

import com.educonnect.common.web.Problems.FieldProblem;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ProblemExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemExceptionHandler.class);

    static final String VALIDATION_MESSAGE = "Girilen bilgiler geçersiz.";

    static final String IDENTITY_HEADER_PREFIX = "X-Authenticated-";

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException ex, HttpServletRequest request) {
        return Problems.response(Problems.create(ex.getStatus(), ex.getErrorCode(), ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        log.debug("Bad request on {}: {}", LogValues.safe(request.getRequestURI()), LogValues.safe(ex.getMessage()));
        return Problems.response(Problems.create(HttpStatus.BAD_REQUEST, "BAD_REQUEST", ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> handleNoSuchElement(NoSuchElementException ex, HttpServletRequest request) {
        return Problems.response(Problems.create(HttpStatus.NOT_FOUND, "NOT_FOUND", ex.getMessage(),
                request.getRequestURI()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest request) {
        List<FieldProblem> errors = ex.getConstraintViolations().stream()
                .map(violation -> new FieldProblem(leafName(violation), violation.getMessage()))
                .toList();
        ProblemDetail problem = Problems.create(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                validationDetail(errors), request.getRequestURI());
        return Problems.response(Problems.withErrors(problem, errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), LogValues.safe(request.getRequestURI()), ex);
        return Problems.response(Problems.create(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", null,
                request.getRequestURI()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldProblem> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.add(new FieldProblem(error.getField(), message(error))));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.add(new FieldProblem(error.getObjectName(), message(error))));
        return validationResponse(ex, errors, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<FieldProblem> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error -> {
            String field = error instanceof FieldError fieldError
                    ? fieldError.getField()
                    : result.getMethodParameter().getParameterName();
            errors.add(new FieldProblem(field, message(error)));
        }));
        return validationResponse(ex, errors, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, "İstek gövdesi okunamadı veya geçersiz.");
        problem.setProperty(Problems.ERROR_CODE, "MALFORMED_REQUEST");
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex,
                                                                          HttpHeaders headers, HttpStatusCode status,
                                                                          WebRequest request) {
        if (ex instanceof MissingRequestHeaderException missing
                && missing.getHeaderName().startsWith(IDENTITY_HEADER_PREFIX)) {
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED,
                    Problems.defaultMessage(HttpStatus.UNAUTHORIZED.value()));
            problem.setProperty(Problems.ERROR_CODE, "UNAUTHENTICATED");
            return handleExceptionInternal(ex, problem, headers, HttpStatus.UNAUTHORIZED, request);
        }
        return super.handleServletRequestBindingException(ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (body == null && ex instanceof ErrorResponse errorResponse) {
            body = errorResponse.updateAndGetBody(getMessageSource(), LocaleContextHolder.getLocale());
        }
        ProblemDetail problem = body instanceof ProblemDetail detail
                ? detail
                : ProblemDetail.forStatus(statusCode);
        if (!(ex instanceof ResponseStatusException) && !hasErrorCode(body)) {
            problem.setDetail(frameworkDetail(ex, statusCode));
        }
        if (statusCode.is5xxServerError()) {
            log.warn("Request failed with {} on {}: {}", statusCode.value(), LogValues.safe(path(request)), LogValues.safe(ex.getMessage()));
        }
        Problems.enrich(problem, null, path(request));
        return super.handleExceptionInternal(ex, problem, headers, statusCode, request);
    }

    private ResponseEntity<Object> validationResponse(Exception ex, List<FieldProblem> errors, HttpHeaders headers,
                                                      HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, validationDetail(errors));
        problem.setProperty(Problems.ERROR_CODE, "VALIDATION_FAILED");
        Problems.withErrors(problem, errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    private static String validationDetail(List<FieldProblem> errors) {
        if (errors.isEmpty()) {
            return VALIDATION_MESSAGE;
        }
        return VALIDATION_MESSAGE + " " + errors.stream()
                .map(error -> error.field() + ": " + error.message())
                .collect(Collectors.joining(", "));
    }

    private static boolean hasErrorCode(Object body) {
        if (!(body instanceof ProblemDetail detail)) {
            return false;
        }
        Map<String, Object> properties = detail.getProperties();
        return properties != null && properties.containsKey(Problems.ERROR_CODE);
    }

    private static String frameworkDetail(Exception ex, HttpStatusCode status) {
        if (ex instanceof MissingServletRequestParameterException missing) {
            return "Zorunlu parametre eksik: " + missing.getParameterName();
        }
        if (ex instanceof MissingServletRequestPartException missing) {
            return "Zorunlu alan eksik: " + missing.getRequestPartName();
        }
        if (ex instanceof TypeMismatchException mismatch && mismatch.getPropertyName() != null) {
            return "Geçersiz parametre değeri: " + mismatch.getPropertyName();
        }
        return Problems.defaultMessage(status.value());
    }

    private static String message(MessageSourceResolvable error) {
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : "geçersiz değer";
    }

    private static String leafName(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }
}
