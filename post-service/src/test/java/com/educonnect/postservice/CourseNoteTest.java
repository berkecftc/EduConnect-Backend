package com.educonnect.postservice;

import com.educonnect.common.test.MinioTestContainer;
import com.educonnect.common.test.TestTokens;
import com.educonnect.postservice.client.ClubClient;
import com.educonnect.postservice.client.CourseClient;
import com.educonnect.postservice.dto.CourseAccess;
import com.educonnect.postservice.model.Post;
import com.educonnect.postservice.model.PostStatus;
import com.educonnect.postservice.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@PostIntegrationTest
@Import(MinioTestContainer.class)
class CourseNoteTest {

    private static final String PDF = "%PDF-1.4\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF\n";

    private final UUID courseId = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();
    private final UUID outsider = UUID.randomUUID();
    private final UUID instructor = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PostRepository postRepository;

    @MockitoBean
    private CourseClient courseClient;

    @MockitoBean
    private ClubClient clubClient;

    @BeforeEach
    void stubCourse() {
        when(courseClient.getAccess(courseId, student)).thenReturn(access(null, false, true));
        when(courseClient.getAccess(courseId, outsider)).thenReturn(access(null, false, false));
        when(courseClient.getAccess(courseId, instructor)).thenReturn(access("COORDINATOR", true, false));
    }

    @Test
    void courseNotesNeedTheSharingDeclarationAndCourseMembership() throws Exception {
        mockMvc.perform(as(post("/api/posts"), TestTokens.student(student)).contentType(MediaType.APPLICATION_JSON)
                        .content(note(null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DECLARATION_REQUIRED"));
        mockMvc.perform(as(post("/api/posts"), TestTokens.student(outsider)).contentType(MediaType.APPLICATION_JSON)
                        .content(note(true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_COURSE_MEMBER"));
        mockMvc.perform(as(post("/api/posts"), TestTokens.student(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Etkinlik\",\"content\":\"x\",\"category\":\"GENEL\",\"courseId\":\"" + courseId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_NOT_ALLOWED"));

        UUID noteId = create(note(true));
        Post note = postRepository.findById(noteId).orElseThrow();
        assertThat(note.getCourseId()).isEqualTo(courseId);
        assertThat(note.getCourseLabel()).isEqualTo("BIL101 Programlama (Şube 1)");
        assertThat(note.getDeclarationAcceptedAt()).isNotNull();

        UUID question = create("{\"title\":\"Ödev 2\",\"content\":\"İpucu?\",\"category\":\"SORU\",\"courseId\":\"" + courseId + "\"}");
        mockMvc.perform(as(put("/api/posts/{id}", question), TestTokens.student(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ödev 2\",\"content\":\"İpucu?\",\"category\":\"GENEL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("SCOPE_NOT_ALLOWED"));
        mockMvc.perform(as(put("/api/posts/{id}", question), TestTokens.student(student)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Ödev 2\",\"content\":\"Özet\",\"category\":\"DERS_NOTU\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DECLARATION_REQUIRED"));
    }

    @Test
    void notesCarryOneAttachmentThatVisibleReadersDownload() throws Exception {
        UUID noteId = create(note(true));
        MockMultipartFile pdf = new MockMultipartFile("file", "hafta3.pdf", "application/pdf", PDF.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(as(multipart(HttpMethod.PUT, "/api/posts/{id}/attachment", noteId).file(pdf),
                        TestTokens.student(outsider)))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(multipart(HttpMethod.PUT, "/api/posts/{id}/attachment", noteId).file(pdf),
                        TestTokens.student(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachmentName").value("hafta3.pdf"));

        Post note = postRepository.findById(noteId).orElseThrow();
        note.setStatus(PostStatus.PUBLISHED);
        postRepository.save(note);

        mockMvc.perform(as(get("/api/posts/{id}", noteId), TestTokens.student(outsider)))
                .andExpect(jsonPath("$.attachmentName").value("hafta3.pdf"))
                .andExpect(jsonPath("$.courseLabel").value("BIL101 Programlama (Şube 1)"));
        mockMvc.perform(as(get("/api/posts/{id}/attachment", noteId), TestTokens.student(outsider)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("hafta3.pdf")))
                .andExpect(content().bytes(PDF.getBytes(StandardCharsets.UTF_8)));

        UUID question = create("{\"title\":\"Soru\",\"content\":\"x\",\"category\":\"SORU\"}");
        mockMvc.perform(as(multipart(HttpMethod.PUT, "/api/posts/{id}/attachment", question).file(pdf),
                        TestTokens.student(student)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ATTACHMENT_NOT_ALLOWED"));

        mockMvc.perform(as(delete("/api/posts/{id}/attachment", noteId), TestTokens.student(student)))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(get("/api/posts/{id}/attachment", noteId), TestTokens.student(outsider)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ATTACHMENT_NOT_FOUND"));
    }

    @Test
    void theCourseInstructorFiltersAndHidesNotesOfTheirCourse() throws Exception {
        UUID noteId = create(note(true));
        Post note = postRepository.findById(noteId).orElseThrow();
        note.setStatus(PostStatus.PUBLISHED);
        postRepository.save(note);

        mockMvc.perform(as(get("/api/posts").param("courseId", courseId.toString()).param("category", "DERS_NOTU"),
                        TestTokens.academician(instructor)))
                .andExpect(jsonPath("$.content[?(@.id == '" + noteId + "')]").exists());
        mockMvc.perform(as(post("/api/posts/{id}/hide", noteId), TestTokens.academician(instructor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Vize soruları paylaşılmış\"}"))
                .andExpect(status().isNoContent());
        assertThat(postRepository.findById(noteId).orElseThrow().getStatus()).isEqualTo(PostStatus.HIDDEN);
    }

    private UUID create(String body) throws Exception {
        String response = mockMvc.perform(as(post("/api/posts"), TestTokens.student(student))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String note(Boolean declared) {
        return "{\"title\":\"Hafta 3 notları\",\"content\":\"Özet\",\"category\":\"DERS_NOTU\",\"courseId\":\"" + courseId + "\""
                + (declared == null ? "" : ",\"sharingDeclaration\":" + declared) + "}";
    }

    private CourseAccess access(String staffRole, boolean teaches, boolean enrolled) {
        return new CourseAccess(courseId, "ACTIVE", teaches, enrolled, staffRole, "BIL101", "Programlama", "1");
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
