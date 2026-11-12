package com.educonnect.userservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.userservice.client.AuthStaffClient;
import com.educonnect.userservice.models.AcademicTitle;
import com.educonnect.userservice.models.Academician;
import com.educonnect.userservice.models.Department;
import com.educonnect.userservice.models.Faculty;
import com.educonnect.userservice.models.Program;
import com.educonnect.userservice.models.ProgramLevel;
import com.educonnect.userservice.models.Student;
import com.educonnect.userservice.repository.AcademicianRepository;
import com.educonnect.userservice.repository.DepartmentRepository;
import com.educonnect.userservice.repository.FacultyRepository;
import com.educonnect.userservice.repository.ProgramRepository;
import com.educonnect.userservice.repository.StudentRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UserIntegrationTest
class ProfileChangeVerificationTest {

    private final UUID student = UUID.randomUUID();
    private final UUID otherStudent = UUID.randomUUID();
    private final UUID teacher = UUID.randomUUID();
    private final UUID studentDesk = UUID.randomUUID();
    private final UUID staffDesk = UUID.randomUUID();
    private final String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AcademicianRepository academicianRepository;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ProgramRepository programRepository;

    @MockitoBean
    private AuthStaffClient authStaffClient;

    @BeforeEach
    void setUp() {
        Program engineering = program("ENG");
        Program medicine = program("MED");
        save(student, engineering.getId());
        save(otherStudent, medicine.getId());
        Academician a = new Academician(teacher, "Kemal", "Öz", null);
        a.setEmail(teacher + "@test.educonnect.local");
        a.setAcademicTitle(AcademicTitle.ASSISTANT_PROFESSOR);
        academicianRepository.save(a);
        UUID engineeringFaculty = departmentRepository.findById(engineering.getDepartmentId()).orElseThrow().getFacultyId();
        when(authStaffClient.grants(studentDesk)).thenReturn(List.of(new AuthStaffClient.StaffGrant("STUDENT_VERIFIER", engineeringFaculty)));
        when(authStaffClient.grants(staffDesk)).thenReturn(List.of(new AuthStaffClient.StaffGrant("STAFF_VERIFIER", null)));
    }

    @Test
    void verifiersDecideOnlyTheRequestsInTheirScope() throws Exception {
        String own = submit(TestTokens.student(student), "{\"lastName\":\"Kara\",\"reason\":\"Evlilik\"}");
        String other = submit(TestTokens.student(otherStudent), "{\"lastName\":\"Ak\",\"reason\":\"Evlilik\"}");
        String staff = submit(TestTokens.academician(teacher), "{\"title\":\"Doç. Dr.\",\"reason\":\"Atama\"}");
        String studentDeskToken = TestTokens.user(studentDesk, "ROLE_STAFF,PERM_STUDENT_VERIFIER");
        String staffDeskToken = TestTokens.user(staffDesk, "ROLE_STAFF,PERM_STAFF_VERIFIER");

        mockMvc.perform(as(get("/api/users/verification/change-requests"), studentDeskToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(own)))
                .andExpect(jsonPath("$[*].id", not(hasItem(other))))
                .andExpect(jsonPath("$[*].id", not(hasItem(staff))));
        mockMvc.perform(as(post("/api/users/verification/change-requests/{id}/approve", other), studentDeskToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("OUT_OF_SCOPE"));
        mockMvc.perform(as(post("/api/users/verification/change-requests/{id}/approve", staff), studentDeskToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/users/verification/change-requests/{id}/approve", own), studentDeskToken))
                .andExpect(status().isOk());
        assertThat(studentRepository.findById(student).orElseThrow().getLastName()).isEqualTo("Kara");

        mockMvc.perform(as(get("/api/users/verification/change-requests"), staffDeskToken))
                .andExpect(jsonPath("$[*].id", hasItem(staff)))
                .andExpect(jsonPath("$[*].id", not(hasItem(other))));
        mockMvc.perform(as(post("/api/users/verification/change-requests/{id}/approve", staff), staffDeskToken))
                .andExpect(status().isOk());
        assertThat(academicianRepository.findById(teacher).orElseThrow().getAcademicTitle()).isEqualTo(AcademicTitle.ASSOCIATE_PROFESSOR);

        mockMvc.perform(as(get("/api/users/verification/change-requests"), TestTokens.student(student)))
                .andExpect(status().isForbidden());
    }

    private String submit(String token, String body) throws Exception {
        return JsonPath.read(mockMvc.perform(as(post("/api/users/profile/me/change-requests"), token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void save(UUID id, UUID programId) {
        Student s = new Student(id, "Ayşe", "Demir", "V" + id.toString().substring(0, 8));
        s.setEmail(id + "@test.educonnect.local");
        s.setProgramId(programId);
        studentRepository.save(s);
    }

    private Program program(String code) {
        Faculty faculty = new Faculty();
        faculty.setCode(code + "F" + suffix);
        faculty.setName(code + " Fakültesi " + suffix);
        faculty.setActive(true);
        faculty = facultyRepository.save(faculty);
        Department department = new Department();
        department.setFacultyId(faculty.getId());
        department.setCode(code + "D" + suffix);
        department.setName(code + " Bölümü " + suffix);
        department.setActive(true);
        department = departmentRepository.save(department);
        Program program = new Program();
        program.setDepartmentId(department.getId());
        program.setCode(code + "P" + suffix);
        program.setName(code + " Lisans");
        program.setLevel(ProgramLevel.BACHELOR);
        program.setDurationYears(4);
        program.setActive(true);
        return programRepository.save(program);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
