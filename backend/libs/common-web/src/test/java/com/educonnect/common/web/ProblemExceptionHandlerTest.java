package com.educonnect.common.web;

import tools.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProblemExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new SecurityProblemHandler(), new DataAccessProblemHandler(),
                        new ProblemExceptionHandler())
                .build();
    }

    @Test
    void apiException_returnsProblemWithMessageAndErrorCode() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Kulüp bulunamadı."))
                .andExpect(jsonPath("$.message").value("Kulüp bulunamadı."))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.errorCode").value("CLUB_NOT_FOUND"))
                .andExpect(jsonPath("$.instance").value("/test/not-found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void responseStatusException_keepsReasonAsMessage() throws Exception {
        mockMvc.perform(get("/test/rse"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.message").value("Bu kulübü yönetme yetkiniz yok."))
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @Test
    void responseStatusExceptionWithoutReason_usesTurkishDefault() throws Exception {
        mockMvc.perform(get("/test/rse-empty"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("İşlem mevcut durumla çakışıyor."));
    }

    @Test
    void invalidBody_returnsFieldErrors() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\",\"count\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.message", containsString("Girilen bilgiler geçersiz.")))
                .andExpect(jsonPath("$.message", containsString("name")));
    }

    @Test
    void invalidRequestParam_returnsFieldErrors() throws Exception {
        mockMvc.perform(get("/test/param").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void malformedJson_returns400WithoutParserDetails() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.message").value("İstek gövdesi okunamadı veya geçersiz."));
    }

    @Test
    void typeMismatch_namesTheParameter() throws Exception {
        mockMvc.perform(get("/test/uuid/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Geçersiz parametre değeri: id"));
    }

    @Test
    void missingParameter_isTranslated() throws Exception {
        mockMvc.perform(get("/test/required"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Zorunlu parametre eksik: q"));
    }

    @Test
    void unsupportedMethod_returns405Problem() throws Exception {
        mockMvc.perform(post("/test/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.message").value("Bu işlem bu adreste desteklenmiyor."));
    }

    @Test
    void illegalArgumentAndNoSuchElement_mapTo400And404() throws Exception {
        mockMvc.perform(get("/test/iae"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Geçersiz tarih."));
        mockMvc.perform(get("/test/nse"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Kullanıcı yok."));
    }

    @Test
    void dataConflicts_map409() throws Exception {
        mockMvc.perform(get("/test/optimistic"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONCURRENT_UPDATE"));
        mockMvc.perform(get("/test/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DATA_CONFLICT"))
                .andExpect(content().string(not(containsString("duplicate key"))));
    }

    @Test
    void securityExceptions_map403And401() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user", null, List.of()));
        try {
            mockMvc.perform(get("/test/denied"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Bu işlem için yetkiniz yok."));
        } finally {
            SecurityContextHolder.clearContext();
        }
        mockMvc.perform(get("/test/bad-credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("E-posta veya parola hatalı."));
    }

    @Test
    void anonymousRequests_map401WhenDeniedOrWithoutTheIdentityHeader() throws Exception {
        mockMvc.perform(get("/test/denied"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        mockMvc.perform(get("/test/identity"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.message").value("Oturum açmanız gerekiyor."));
        mockMvc.perform(get("/test/identity").header("X-Authenticated-User-Id", "u-1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/test/other-header"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unexpectedException_hidesInternalMessage() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Beklenmeyen bir hata oluştu."))
                .andExpect(content().string(not(containsString("minio-secret"))))
                .andExpect(content().string(not(containsString("RuntimeException"))));
    }

    @Test
    void securityFilterHandlers_writeProblemJson() throws Exception {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        ProblemSecurityHandlers handlers = new ProblemSecurityHandlers(objectMapper);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/clubs/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        handlers.accessDeniedHandler().handle(request, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        Map<?, ?> body = objectMapper.readValue(response.getContentAsByteArray(), Map.class);
        assertThat(body.get("message")).isEqualTo("Bu işlem için yetkiniz yok.");
        assertThat(body.get("errorCode")).isEqualTo("ACCESS_DENIED");
        assertThat(body.get("instance")).isEqualTo("/api/clubs/1");
        assertThat(body.get("type")).isEqualTo("about:blank");

        MockHttpServletResponse unauthenticated = new MockHttpServletResponse();
        handlers.authenticationEntryPoint().commence(request, unauthenticated,
                new BadCredentialsException("no identity"));
        assertThat(unauthenticated.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        Map<?, ?> unauthenticatedBody = objectMapper.readValue(unauthenticated.getContentAsByteArray(), Map.class);
        assertThat(unauthenticatedBody.get("message")).isEqualTo("Oturum açmanız gerekiyor.");
    }

    record TestBody(@NotBlank String name, @Min(1) int count) {
    }

    @RestController
    static class TestController {

        @GetMapping("/test/not-found")
        String notFound() {
            throw new NotFoundException("CLUB_NOT_FOUND", "Kulüp bulunamadı.");
        }

        @GetMapping("/test/rse")
        String rse() {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu kulübü yönetme yetkiniz yok.");
        }

        @GetMapping("/test/rse-empty")
        String rseEmpty() {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }

        @PostMapping("/test/body")
        String body(@Valid @RequestBody TestBody body) {
            return "ok";
        }

        @GetMapping("/test/param")
        String param(@RequestParam @Min(1) int size) {
            return "ok";
        }

        @GetMapping("/test/uuid/{id}")
        String uuid(@PathVariable UUID id) {
            return "ok";
        }

        @GetMapping("/test/required")
        String required(@RequestParam String q) {
            return q;
        }

        @GetMapping("/test/iae")
        String iae() {
            throw new IllegalArgumentException("Geçersiz tarih.");
        }

        @GetMapping("/test/nse")
        String nse() {
            throw new NoSuchElementException("Kullanıcı yok.");
        }

        @GetMapping("/test/optimistic")
        String optimistic() {
            throw new OptimisticLockingFailureException("row was updated");
        }

        @GetMapping("/test/integrity")
        String integrity() {
            throw new DataIntegrityViolationException("duplicate key value violates unique constraint");
        }

        @GetMapping("/test/denied")
        String denied() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/test/identity")
        String identity(@RequestHeader("X-Authenticated-User-Id") String userId) {
            return userId;
        }

        @GetMapping("/test/other-header")
        String otherHeader(@RequestHeader("X-Client-Version") String version) {
            return version;
        }

        @GetMapping("/test/bad-credentials")
        String badCredentials() {
            throw new BadCredentialsException("bad");
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new RuntimeException("minio-secret leaked in message");
        }
    }
}
