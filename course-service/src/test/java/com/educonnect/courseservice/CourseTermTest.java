package com.educonnect.courseservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.courseservice.service.StaffEligibility;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@CourseIntegrationTest
class CourseTermTest {

    @MockitoBean
    private StaffEligibility staffEligibility;

    private final UUID admin = UUID.randomUUID();
    private final UUID instructor = UUID.randomUUID();
    private final UUID student = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void onlyAdminsManageTermsAndDatesAreValidated() throws Exception {
        int year = ThreadLocalRandom.current().nextInt(2100, 2190);
        String spring = term(year, "SPRING", (year) + "-02-15", (year) + "-06-30");
        mockMvc.perform(json(post("/api/courses/terms"), TestTokens.academician(instructor), spring))
                .andExpect(status().isForbidden());
        mockMvc.perform(json(post("/api/courses/terms"), TestTokens.admin(admin),
                        term(year, "SUMMER", year + "-08-30", year + "-07-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TERM_DATES"));
        mockMvc.perform(json(post("/api/courses/terms"), TestTokens.admin(admin), spring))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.label").value((year - 1) + "-" + year + " Bahar"));
        mockMvc.perform(json(post("/api/courses/terms"), TestTokens.admin(admin), spring))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TERM_EXISTS"));
        mockMvc.perform(as(get("/api/courses/terms/current"), TestTokens.student(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TestCourses.DEFAULT_TERM.toString()));
    }

    @Test
    void coursesAreOpenedPerTermAndSectionFromTheCatalog() throws Exception {
        int year = ThreadLocalRandom.current().nextInt(2100, 2190);
        String springId = JsonPath.read(mockMvc.perform(json(post("/api/courses/terms"), TestTokens.admin(admin),
                        term(year, "SPRING", year + "-02-15", year + "-06-30")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        String code = "BIL" + ThreadLocalRandom.current().nextInt(100000, 999999);

        String fall = open(code, null, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.termId").value(TestCourses.DEFAULT_TERM.toString()))
                .andExpect(jsonPath("$.termLabel").value("2026-2027 Güz"))
                .andExpect(jsonPath("$.section").value("1"))
                .andExpect(jsonPath("$.code").value(code))
                .andReturn().getResponse().getContentAsString();
        String catalogId = JsonPath.read(fall, "$.catalogCourseId");
        open(code, null, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_COURSE_CODE"));
        open(code, null, "2").andExpect(status().isOk()).andExpect(jsonPath("$.section").value("2"));
        open(code.toLowerCase(Locale.ROOT), springId, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.catalogCourseId").value(catalogId))
                .andExpect(jsonPath("$.termLabel").value((year - 1) + "-" + year + " Bahar"));

        mockMvc.perform(as(get("/api/courses/catalog").param("q", code), TestTokens.academician(instructor)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(catalogId));
        mockMvc.perform(as(get("/api/courses").param("termId", springId), TestTokens.student(student)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[*].termId", everyItem(is(springId))));
        mockMvc.perform(as(get("/api/courses").param("termId", TestCourses.DEFAULT_TERM.toString()), TestTokens.student(student)))
                .andExpect(jsonPath("$[*].code", hasItem(code)));

        mockMvc.perform(json(put("/api/courses/terms/{id}", springId), TestTokens.admin(admin),
                        term(year, "SUMMER", year + "-07-01", year + "-08-30")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("TERM_IN_USE"));
        mockMvc.perform(as(delete("/api/courses/terms/{id}", springId), TestTokens.admin(admin)))
                .andExpect(status().isConflict());
    }

    private ResultActions open(String code, String termId, String section) throws Exception {
        String json = "{\"title\":\"Algoritmalar\",\"code\":\"" + code + "\",\"credit\":4,\"capacity\":20,\"instructorId\":\""
                + instructor + "\"" + (termId != null ? ",\"termId\":\"" + termId + "\"" : "")
                + (section != null ? ",\"section\":\"" + section + "\"" : "") + "}";
        MockMultipartFile part = new MockMultipartFile("course", "", MediaType.APPLICATION_JSON_VALUE, json.getBytes());
        return mockMvc.perform(as(multipart("/api/courses").file(part), TestTokens.academician(instructor)));
    }

    private static String term(int year, String season, String startsOn, String endsOn) {
        return "{\"academicYear\":" + year + ",\"season\":\"" + season + "\",\"startsOn\":\"" + startsOn
                + "\",\"endsOn\":\"" + endsOn + "\"}";
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B json(B request, String token, String body) {
        return as(request, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
