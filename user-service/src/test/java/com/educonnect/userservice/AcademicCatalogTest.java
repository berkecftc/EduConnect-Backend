package com.educonnect.userservice;

import com.educonnect.common.test.TestTokens;
import com.educonnect.userservice.dto.message.AcademicianProfileMessage;
import com.educonnect.userservice.dto.message.UserRegisteredMessage;
import com.educonnect.userservice.listener.ProfileCreationListener;
import com.educonnect.userservice.models.Department;
import com.educonnect.userservice.models.Faculty;
import com.educonnect.userservice.models.Program;
import com.educonnect.userservice.models.ProgramLevel;
import com.educonnect.userservice.repository.AcademicianRepository;
import com.educonnect.userservice.repository.DepartmentRepository;
import com.educonnect.userservice.repository.FacultyRepository;
import com.educonnect.userservice.repository.ProgramRepository;
import com.educonnect.userservice.repository.StudentRepository;
import com.educonnect.userservice.service.AcademicCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.time.Month;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@UserIntegrationTest
class AcademicCatalogTest {

    private final UUID admin = UUID.randomUUID();
    private final String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FacultyRepository facultyRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private ProgramRepository programRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AcademicianRepository academicianRepository;

    @Autowired
    private ProfileCreationListener listener;

    @Autowired
    private AcademicCatalogService catalogService;

    @Test
    void onlyAdminsManageTheCatalogAndThePublicSeesActiveUnits() throws Exception {
        String faculty = "{\"code\":\"MF" + suffix + "\",\"name\":\"Mühendislik " + suffix + "\"}";
        mockMvc.perform(as(post("/api/users/academic/faculties"), TestTokens.student(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON).content(faculty))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(post("/api/users/academic/faculties"), TestTokens.admin(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(faculty))
                .andExpect(status().isCreated());
        mockMvc.perform(as(post("/api/users/academic/faculties"), TestTokens.admin(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(faculty.replace("MF", "mf")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CODE_EXISTS"));
        Faculty saved = facultyRepository.findAll().stream().filter(f -> f.getCode().equals("MF" + suffix)).findFirst().orElseThrow();

        mockMvc.perform(as(post("/api/users/academic/departments"), TestTokens.admin(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + UUID.randomUUID() + "\",\"code\":\"BM" + suffix + "\",\"name\":\"Bilgisayar\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("FACULTY_NOT_FOUND"));
        mockMvc.perform(as(post("/api/users/academic/departments"), TestTokens.admin(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + saved.getId() + "\",\"code\":\"BM" + suffix + "\",\"name\":\"Bilgisayar\"}"))
                .andExpect(status().isCreated());
        Department department = departmentRepository.findAll().stream().filter(d -> d.getCode().equals("BM" + suffix)).findFirst().orElseThrow();

        mockMvc.perform(as(post("/api/users/academic/programs"), TestTokens.admin(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + department.getId() + "\",\"code\":\"BMP" + suffix + "\",\"name\":\"Lisans\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PROGRAM_LEVEL_REQUIRED"));
        mockMvc.perform(as(post("/api/users/academic/programs"), TestTokens.admin(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + department.getId() + "\",\"code\":\"BMP" + suffix
                                + "\",\"name\":\"Lisans\",\"level\":\"BACHELOR\",\"durationYears\":9}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(as(post("/api/users/academic/programs"), TestTokens.admin(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":\"" + department.getId() + "\",\"code\":\"BMP" + suffix
                                + "\",\"name\":\"Lisans\",\"level\":\"BACHELOR\",\"durationYears\":4}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/users/academic/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'MF" + suffix + "')].departments[0].programs[0].level").value("BACHELOR"));

        mockMvc.perform(as(put("/api/users/academic/faculties/{id}", saved.getId()), TestTokens.admin(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"MF" + suffix + "\",\"name\":\"Mühendislik\",\"active\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/users/academic/catalog"))
                .andExpect(jsonPath("$[*].code", not(hasItem("MF" + suffix))));
        mockMvc.perform(as(get("/api/users/academic/catalog/all"), TestTokens.student(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/users/academic/catalog/all"), TestTokens.admin(admin)))
                .andExpect(jsonPath("$[?(@.code == 'MF" + suffix + "')].active").value(false));
    }

    @Test
    void servicesResolvePlacementsAndInactiveUnitsAreFlagged() throws Exception {
        Program program = program(true);
        mockMvc.perform(as(get("/api/users/internal/academic/programs/{id}", program.getId()), TestTokens.student(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/users/internal/academic/programs/{id}", program.getId()), TestTokens.service("auth-service")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value("Fizik " + suffix))
                .andExpect(jsonPath("$.durationYears").value(4))
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(as(get("/api/users/internal/academic/programs/{id}", UUID.randomUUID()), TestTokens.service("auth-service")))
                .andExpect(status().isNotFound());

        Program closed = program(false);
        mockMvc.perform(as(get("/api/users/internal/academic/programs/{id}", closed.getId()), TestTokens.service("auth-service")))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(as(get("/api/users/internal/academic/departments/{id}", program.getDepartmentId()), TestTokens.service("auth-service")))
                .andExpect(jsonPath("$.facultyName").value("Fen " + suffix))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void newProfilesCarryTheirPlacementAndClassYear() throws Exception {
        Program program = program(true);
        UUID studentId = UUID.randomUUID();
        UserRegisteredMessage message = new UserRegisteredMessage();
        message.setUserId(studentId);
        message.setFirstName("Elif");
        message.setLastName("Kaya");
        message.setEmail(studentId + "@test.educonnect.local");
        message.setRoles(Set.of("ROLE_STUDENT"));
        message.setStudentNumber("N" + suffix);
        message.setDepartment("eski metin");
        message.setProgramId(program.getId());
        int entryYear = LocalDate.now().getYear() - 1;
        message.setEntryYear(entryYear);
        listener.handleProfileCreation(message);

        assertThat(studentRepository.findById(studentId).orElseThrow().getDepartment()).isEqualTo("Fizik " + suffix);
        int expectedClass = LocalDate.now().getMonthValue() >= Month.SEPTEMBER.getValue() ? 2 : 1;
        assertThat(catalogService.classYear(entryYear)).isEqualTo(expectedClass);
        mockMvc.perform(as(get("/api/users/profile/{id}", studentId), TestTokens.student(studentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programName").value("Fizik Lisans"))
                .andExpect(jsonPath("$.programLevel").value("BACHELOR"))
                .andExpect(jsonPath("$.facultyName").value("Fen " + suffix))
                .andExpect(jsonPath("$.entryYear").value(entryYear))
                .andExpect(jsonPath("$.classYear").value(expectedClass));

        UUID teacherId = UUID.randomUUID();
        AcademicianProfileMessage academician = new AcademicianProfileMessage();
        academician.setUserId(teacherId);
        academician.setFirstName("Kemal");
        academician.setLastName("Hoca");
        academician.setEmail(teacherId + "@test.educonnect.local");
        academician.setDepartment("serbest");
        academician.setDepartmentId(program.getDepartmentId());
        listener.handleAcademicianProfileCreation(academician);
        assertThat(academicianRepository.findById(teacherId).orElseThrow())
                .satisfies(a -> assertThat(a.getDepartmentId()).isEqualTo(program.getDepartmentId()))
                .satisfies(a -> assertThat(a.getDepartment()).isEqualTo("Fizik " + suffix));

        UUID orphan = UUID.randomUUID();
        message.setUserId(orphan);
        message.setEmail(orphan + "@test.educonnect.local");
        message.setStudentNumber("O" + suffix);
        message.setProgramId(UUID.randomUUID());
        listener.handleProfileCreation(message);
        assertThat(studentRepository.findById(orphan).orElseThrow().getProgramId()).isNull();
    }

    private Program program(boolean active) {
        Faculty faculty = facultyRepository.findAll().stream().filter(f -> f.getCode().equals("FEN" + suffix)).findFirst()
                .orElseGet(() -> {
                    Faculty created = new Faculty();
                    created.setCode("FEN" + suffix);
                    created.setName("Fen " + suffix);
                    created.setActive(true);
                    return facultyRepository.save(created);
                });
        Department department = departmentRepository.findAll().stream().filter(d -> d.getCode().equals("FZ" + suffix)).findFirst()
                .orElseGet(() -> {
                    Department created = new Department();
                    created.setFacultyId(faculty.getId());
                    created.setCode("FZ" + suffix);
                    created.setName("Fizik " + suffix);
                    created.setActive(true);
                    return departmentRepository.save(created);
                });
        Program program = new Program();
        program.setDepartmentId(department.getId());
        program.setCode((active ? "FZL" : "FZK") + suffix);
        program.setName("Fizik Lisans");
        program.setLevel(ProgramLevel.BACHELOR);
        program.setDurationYears(4);
        program.setActive(active);
        return programRepository.save(program);
    }

    private static <B extends AbstractMockHttpServletRequestBuilder<B>> B as(B request, String token) {
        return request.header(HttpHeaders.AUTHORIZATION, TestTokens.bearer(token));
    }
}
